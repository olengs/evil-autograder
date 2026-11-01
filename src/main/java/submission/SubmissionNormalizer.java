package submission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;

public class SubmissionNormalizer { //rearranges one extracted zip into <username>/<question folder>/<answer file> > records every correction as an issue
    private static final int MAX_SEARCH_DEPTH = 8; //deepest wrapper folder chain to search
    private static final String MAC_ARCHIVE_FOLDER = "__MACOSX";
    private static final Pattern NAME = fieldPattern("Name");
    private static final Pattern EMAIL = fieldPattern("Email(?:\\h+ID)?");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * only touches the extracted working copies > never the original zips
     * corrections become warnings so grading continues > anything that would need a guess becomes an error
     *
     * @param extracted > submissions from the loader > a zip that failed to extract keeps its issue & passes through
     * @param assessment > folders & files each submission should contain
     * @return > same order > each rooted at its normalized folder \ at its extracted folder if the layout cannot be resolved
     */
    public static List<StudentSubmission> normalize(List<StudentSubmission> extracted, AssessmentDefinition assessment) {
        List<StudentSubmission> normalized = new ArrayList<>();
        for (StudentSubmission submission : extracted) {
            normalized.add(Files.isDirectory(submission.getRootDirectory()) ? normalizeOne(submission, assessment) : submission);
        }
        return normalized;
    }

    private static StudentSubmission normalizeOne(StudentSubmission extracted, AssessmentDefinition assessment) {
        Path extractedRoot = extracted.getRootDirectory().toAbsolutePath().normalize();
        Path root = extractedRoot;
        List<SubmissionIssue> issues = new ArrayList<>();
        Path answerRoot = extractedRoot;

        try {
            Map<String, List<QuestionDefinition>> questions = groupByFolder(assessment);
            validatePaths(extractedRoot, questions);
            root = findAnswerRoot(extractedRoot, questions, extracted.getStudent(), templateFolderNames(assessment));
            answerRoot = root;
            arrangeQuestionFolders(root, questions, issues);
            relocateStrayAnswers(extractedRoot, root, questions, issues);
            correctFileNames(root, questions, issues);
            Path previousRoot = root;
            root = moveToStudentFolder(extractedRoot, root, extracted.getStudent(), issues);
            rebaseIssues(issues, previousRoot, root);
            addMissingHeaders(root, questions, extracted.getStudent(), issues);
        } catch (IOException | RuntimeException error) {
            issues.add(normalizationIssue("normalization error", IssueSeverity.ERROR, error.toString(), null, root));
        }

        StudentSubmission normalized = new StudentSubmission(extracted.getStudent(), extracted.getOriginalZip(), root);
        List<SubmissionIssue> earlier = new ArrayList<>(extracted.getSubmissionIssues());
        rebaseIssues(earlier, answerRoot, root);
        normalized.addIssues(earlier);
        normalized.addIssues(issues);
        return normalized;
    }

    private static Map<String, List<QuestionDefinition>> groupByFolder(AssessmentDefinition assessment) {
        Map<String, List<QuestionDefinition>> questions = new LinkedHashMap<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            questions.computeIfAbsent(question.getExpectedFolder(), key -> new ArrayList<>()).add(question);
        }
        return questions;
    }

    private static Set<String> templateFolderNames(AssessmentDefinition assessment) {
        Set<String> names = new LinkedHashSet<>(List.of(assessment.getName()));
        for (QuestionDefinition question : assessment.getQuestions()) {
            Path template = question.getResourceDirectory();
            for (Path ignored : Path.of(question.getExpectedFolder())) template = template.getParent();
            if (template != null && template.getFileName() != null) names.add(template.getFileName().toString());
        }
        return names;
    }

    private static void validatePaths(Path root, Map<String, List<QuestionDefinition>> questions) throws IOException {
        for (var entry : questions.entrySet()) {
            Path folder = resolveContained(root, entry.getKey());
            for (QuestionDefinition question : entry.getValue()) {
                for (String required : question.getRequiredFiles()) resolveContained(folder, required);
                for (String resource : question.getAllowedResourceFiles()) resolveContained(folder, resource);
            }
        }
    }

    //#region answer root

    //picks the folder recognising the most questions > shallowest wins so q1/q1/ does not beat its parent
    private static Path findAnswerRoot(Path extractedRoot, Map<String, List<QuestionDefinition>> questions,
            StudentDetails student, Set<String> templateFolders) throws IOException {
        int mostRecognised = 0;
        List<Path> candidates = new ArrayList<>();

        for (Path folder : searchableFolders(extractedRoot)) {
            int recognised = recognisedQuestions(folder, questions).size();
            if (recognised > mostRecognised) {
                mostRecognised = recognised;
                candidates.clear();
            }
            if (recognised > 0 && recognised == mostRecognised) {
                candidates.add(folder);
            }
        }
        if (candidates.isEmpty()) {
            return extractedRoot; //question rules report what is missing
        }

        int shallowest = candidates.stream().mapToInt(Path::getNameCount).min().getAsInt();
        candidates = candidates.stream().filter(folder -> folder.getNameCount() == shallowest).toList();
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        if (student != null) {
            List<Path> named = withName(candidates, student.getUsername());
            if (named.size() == 1) {
                return named.getFirst();
            }
        }
        //a working folder beside the untouched template is not ambiguous > any other tie would be a guess
        List<Path> withoutTemplate = candidates.stream()
                .filter(folder -> templateFolders.stream().noneMatch(folder.getFileName().toString()::equalsIgnoreCase)).toList();
        if (withoutTemplate.size() == 1) {
            return withoutTemplate.getFirst();
        }
        throw new IOException("ambiguous submission layout > " + candidates.size() + " folders hold the same answers");
    }

    private static List<Path> withName(List<Path> folders, String name) {
        return folders.stream().filter(folder -> folder.getFileName().toString().equalsIgnoreCase(name)).toList();
    }

    private static Set<String> recognisedQuestions(Path folder, Map<String, List<QuestionDefinition>> questions) throws IOException {
        Set<String> recognised = new LinkedHashSet<>(foldersOfAnswersIn(folder, questions));
        Set<String> namedQuestions = new LinkedHashSet<>();
        questions.keySet().stream().filter(expected -> Path.of(expected).getFileName().toString()
                .equalsIgnoreCase(folder.getFileName().toString())).forEach(namedQuestions::add);
        if (!namedQuestions.isEmpty()) recognised.retainAll(namedQuestions);

        for (String expected : questions.keySet()) {
            boolean hasFolder = entriesIgnoringCase(folder, expected).stream()
                    .anyMatch(candidate -> Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS));
            if (hasFolder) recognised.add(expected);
        }

        for (Path child : subfolders(folder)) {
            String name = child.getFileName().toString();
            questions.keySet().stream().filter(name::equalsIgnoreCase).forEach(recognised::add);

            //a child holding answers for several questions is a flat answer folder > not one question folder
            Set<String> answers = foldersOfAnswersIn(child, questions);
            if (answers.size() == 1) {
                recognised.addAll(answers);
            }
        }
        return recognised;
    }

    private static Set<String> foldersOfAnswersIn(Path folder, Map<String, List<QuestionDefinition>> questions) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Map<String, Set<String>> foldersByAnswer = new LinkedHashMap<>();
        for (var entry : questions.entrySet()) {
            for (QuestionDefinition question : entry.getValue()) {
                for (String required : question.getRequiredFiles()) {
                    foldersByAnswer.computeIfAbsent(required.toLowerCase(java.util.Locale.ROOT), ignored -> new LinkedHashSet<>())
                            .add(entry.getKey());
                }
            }
        }
        for (var answer : foldersByAnswer.entrySet()) {
            boolean hasAnswer = entriesIgnoringCase(folder, answer.getKey()).stream()
                    .anyMatch(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS));
            if (!hasAnswer) continue;
            if (answer.getValue().size() == 1) {
                found.addAll(answer.getValue());
            } else {
                // A common Solution.java must not make one question folder look like the entire assessment root.
                List<String> named = answer.getValue().stream().filter(expected -> Path.of(expected).getFileName().toString()
                        .equalsIgnoreCase(folder.getFileName().toString())).toList();
                if (named.size() == 1) found.add(named.getFirst());
            }
        }
        return found;
    }

    //#endregion

    //#region question folders & files

    private static void arrangeQuestionFolders(Path root, Map<String, List<QuestionDefinition>> questions,
            List<SubmissionIssue> issues) throws IOException {
        for (var entry : questions.entrySet()) {
            String expected = entry.getKey();
            Path named;
            try {
                named = correctPathCase(root, expected, entry.getValue(), "question folder normalized", issues);
            } catch (IOException error) { //only this folder's questions are affected > the rest can still be graded
                addIssue(issues, entry.getValue(), "normalization error", IssueSeverity.ERROR, error.toString(), root);
                continue;
            }
            if (named != null && Files.isDirectory(named, LinkOption.NOFOLLOW_LINKS)) continue;
            List<Path> folders = subfolders(root); //relisted because earlier questions may have renamed a folder
            List<Path> matches = foldersHoldingOnly(expected, folders, questions);
            if (matches.size() > 1) {
                addIssue(issues, entry.getValue(), "normalization collision", IssueSeverity.ERROR,
                        "multiple folders map to " + expected + " > " + fileNames(matches), root);
            } else if (matches.size() == 1) {
                Path folder = matches.getFirst();
                try {
                    Path destination = resolveContained(root, expected);
                    Files.createDirectories(destination.getParent());
                    if (!folder.equals(destination)) {
                        Files.move(folder, destination);
                        addIssue(issues, entry.getValue(), "question folder normalized", IssueSeverity.WARNING,
                                folder.getFileName() + " > " + expected, destination);
                    }
                } catch (IOException error) {
                    addIssue(issues, entry.getValue(), "normalization error", IssueSeverity.ERROR, error.toString(), folder);
                }
            }
        }
    }

    private static List<Path> foldersHoldingOnly(String expected, List<Path> folders,
            Map<String, List<QuestionDefinition>> questions) throws IOException {
        List<Path> matches = new ArrayList<>();
        for (Path folder : folders) {
            String name = folder.getFileName().toString();
            boolean namedForQuestion = questions.keySet().stream()
                    .map(expectedFolder -> Path.of(expectedFolder).getFileName().toString()).anyMatch(name::equalsIgnoreCase);
            if (!namedForQuestion && foldersOfAnswersIn(folder, questions).equals(Set.of(expected))) {
                matches.add(folder);
            }
        }
        return matches;
    }

    //covers loose files & answers saved under the wrong question > a file already in place is never replaced
    private static void relocateStrayAnswers(Path extractedRoot, Path root, Map<String, List<QuestionDefinition>> questions,
            List<SubmissionIssue> issues) throws IOException {
        List<Path> folders = searchableFolders(extractedRoot);

        for (var entry : questions.entrySet()) {
            Path questionFolder = root.resolve(entry.getKey());

            for (QuestionDefinition question : entry.getValue()) {
                for (String requiredFile : question.getRequiredFiles()) {
                    if (Files.isDirectory(questionFolder) && !filesNamed(List.of(questionFolder), requiredFile).isEmpty()) {
                        continue;
                    }
                    List<Path> copies = unassignedFiles(filesNamed(folders, requiredFile), root, questions);
                    boolean matchedByBasename = copies.isEmpty();
                    if (matchedByBasename) {
                        copies = unassignedFiles(filesNamed(folders, Path.of(requiredFile).getFileName().toString()), root, questions);
                    }
                    if (!copies.isEmpty() && !answerPathIsUnique(requiredFile, matchedByBasename, questions)) {
                        issues.add(normalizationIssue("ambiguous answer file", IssueSeverity.ERROR,
                                "cannot assign shared answer filename " + requiredFile + " to " + entry.getKey(), question.getId(), root));
                        continue;
                    }
                    if (copies.size() > 1) {
                        issues.add(normalizationIssue("ambiguous answer file", IssueSeverity.ERROR,
                                copies.size() + " copies of " + requiredFile + " found outside " + entry.getKey(), question.getId(), root));
                    } else if (copies.size() == 1) {
                        Path target = resolveContained(questionFolder, requiredFile);
                        Files.createDirectories(target.getParent());
                        Files.move(copies.getFirst(), target);
                        issues.add(normalizationIssue("answer file relocated", IssueSeverity.WARNING,
                                extractedRoot.relativize(copies.getFirst()) + " > " + entry.getKey() + "/" + requiredFile, question.getId(), target));
                    }
                }
            }
        }
    }

    private static List<Path> filesNamed(List<Path> folders, String fileName) throws IOException {
        List<Path> matches = new ArrayList<>(); //not a set > windows paths compare equal across spellings
        for (Path folder : folders) {
            for (Path file : entriesIgnoringCase(folder, fileName)) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) matches.add(file);
            }
        }
        return matches;
    }

    private static boolean answerPathIsUnique(String required, boolean basenameOnly,
            Map<String, List<QuestionDefinition>> questions) {
        String basename = Path.of(required).getFileName().toString();
        Set<String> destinations = new LinkedHashSet<>();
        for (var entry : questions.entrySet()) {
            for (QuestionDefinition question : entry.getValue()) {
                for (String file : question.getRequiredFiles()) {
                    if (basenameOnly ? Path.of(file).getFileName().toString().equalsIgnoreCase(basename)
                            : file.equalsIgnoreCase(required)) {
                        destinations.add((entry.getKey() + "/" + file).toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        }
        return destinations.size() == 1;
    }

    private static List<Path> unassignedFiles(List<Path> files, Path root,
            Map<String, List<QuestionDefinition>> questions) throws IOException {
        Set<Path> assigned = new LinkedHashSet<>();
        for (var entry : questions.entrySet()) {
            for (Path folder : entriesIgnoringCase(root, entry.getKey())) {
                for (QuestionDefinition question : entry.getValue()) {
                    List<String> declared = new ArrayList<>(question.getRequiredFiles());
                    declared.addAll(question.getAllowedResourceFiles());
                    for (String file : declared) {
                        assigned.addAll(entriesIgnoringCase(folder, file));
                    }
                }
            }
        }
        return files.stream().filter(file -> !assigned.contains(file)).toList();
    }

    private static void correctFileNames(Path root, Map<String, List<QuestionDefinition>> questions, List<SubmissionIssue> issues) {
        for (var entry : questions.entrySet()) {
            Path folder = root.resolve(entry.getKey());
            if (!Files.isDirectory(folder)) {
                continue;
            }

            Set<String> expectedNames = new LinkedHashSet<>();
            for (QuestionDefinition question : entry.getValue()) {
                expectedNames.addAll(question.getRequiredFiles());
                expectedNames.addAll(question.getAllowedResourceFiles());
            }
            for (String expected : expectedNames) {
                try {
                    correctFileName(folder, expected, entry.getValue(), issues);
                } catch (IOException error) {
                    addIssue(issues, entry.getValue(), "normalization error", IssueSeverity.ERROR, error.toString(), folder);
                }
            }
        }
    }

    private static void correctFileName(Path folder, String expected, List<QuestionDefinition> questions,
            List<SubmissionIssue> issues) throws IOException {
        correctPathCase(folder, expected, questions, "filename normalized", issues);
    }

    // Resolve and repair each component; renaming src/Solution.java must not flatten it into one filename.
    private static Path correctPathCase(Path root, String relative, List<QuestionDefinition> questions,
            String code, List<SubmissionIssue> issues) throws IOException {
        resolveContained(root, relative);
        Path current = root;
        for (Path component : Path.of(relative)) {
            Path actual = matchingEntry(current, component.toString());
            if (actual == null) return null;
            if (rename(actual, component.toString())) {
                addIssue(issues, questions, code, IssueSeverity.WARNING,
                        actual.getFileName() + " > " + component, current.resolve(component));
            }
            current = current.resolve(component);
        }
        return current;
    }

    //searching keeps every spelling > only renaming a declared path treats two spellings as a collision
    private static List<Path> entriesIgnoringCase(Path root, String relative) throws IOException {
        resolveContained(root, relative);
        List<Path> current = List.of(root);
        for (Path component : Path.of(relative)) {
            List<Path> next = new ArrayList<>();
            for (Path parent : current) {
                next.addAll(matchingEntries(parent, component.toString()));
            }
            current = next;
        }
        return current;
    }

    private static Path matchingEntry(Path parent, String name) throws IOException {
        List<Path> matches = matchingEntries(parent, name);
        if (matches.size() > 1) throw new IOException("normalization collision > multiple entries map to " + name + " in " + parent);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private static List<Path> matchingEntries(Path parent, String name) throws IOException {
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Path> matches = visibleEntries(parent).stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).toList();
        for (Path match : matches) {
            resolveContained(parent, match.getFileName().toString());
        }
        return matches;
    }

    //#endregion

    //the normalized folder is a sibling of the extraction > a wrapper named after the student can never be in the way
    private static Path moveToStudentFolder(Path extractedRoot, Path root, StudentDetails student,
            List<SubmissionIssue> issues) throws IOException {
        if (student == null) {
            return root;
        }
        Path normalizedParent = extractedRoot.resolveSibling(extractedRoot.getFileName() + "-normalized");
        Path target = Files.createDirectories(normalizedParent).resolve(student.getUsername());
        Files.move(root, target);

        if (!root.getFileName().toString().equals(student.getUsername())) {
            String original = root.equals(extractedRoot) ? "top level of zip" : extractedRoot.relativize(root).toString();
            issues.add(normalizationIssue("submission root normalized", IssueSeverity.WARNING,
                    original + " > " + student.getUsername(), null, target));
        }
        return target;
    }

    private static void addMissingHeaders(Path root, Map<String, List<QuestionDefinition>> questions, StudentDetails student,
            List<SubmissionIssue> issues) {
        if (student == null) {
            return; //an unknown student has no scoresheet details to write
        }
        for (var entry : questions.entrySet()) {
            for (QuestionDefinition question : entry.getValue()) {
                for (String requiredFile : question.getRequiredFiles()) {
                    Path file = root.resolve(entry.getKey()).resolve(requiredFile);
                    try {
                        if (requiredFile.endsWith(".java") && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                && addMissingFields(file, student)) {
                            issues.add(normalizationIssue("student header normalized", IssueSeverity.WARNING,
                                    "added missing details from scoresheet > " + requiredFile, question.getId(), file));
                        }
                    } catch (IOException error) {
                        issues.add(normalizationIssue("normalization error", IssueSeverity.ERROR, error.toString(), question.getId(), file));
                    }
                }
            }
        }
    }

    private static SubmissionIssue normalizationIssue(String code, IssueSeverity severity,
            String message, String questionId, Path path) {
        return new SubmissionIssue(code, severity, message, questionId, path);
    }

    private static void rebaseIssues(List<SubmissionIssue> issues, Path previousRoot, Path newRoot) {
        if (previousRoot.equals(newRoot)) return;
        for (int i = 0; i < issues.size(); i++) {
            SubmissionIssue issue = issues.get(i);
            Path path = issue.getAffectedPath();
            if (path != null && path.toAbsolutePath().normalize().startsWith(previousRoot)) {
                Path moved = newRoot.resolve(previousRoot.relativize(path.toAbsolutePath().normalize()));
                issues.set(i, new SubmissionIssue(issue.getCode(), issue.getSeverity(), issue.getMessage(), issue.getQuestionId(), moved));
            }
        }
    }

    //#region student header

    //conflicting details are left for validation to report
    private static boolean addMissingFields(Path file, StudentDetails student) throws IOException {
        byte[] original = Files.readAllBytes(file);
        //decoding is only for reading the header > bytes are kept so a non utf-8 file reaches javac unchanged
        String source = new String(original, StandardCharsets.UTF_8);
        String header = leadingComments(source.startsWith("﻿") ? source.substring(1) : source);
        List<String> names = values(header, NAME);
        List<String> emails = values(header, EMAIL);
        if (conflicts(names, student.getName()) || conflicts(emails, student.getEmail())) {
            return false; //possible copied work > validation reports it
        }

        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder missing = new StringBuilder();
        if (names.isEmpty() && isUsable(student.getName())) {
            missing.append("// Name: ").append(student.getName().strip()).append(newline);
        }
        if (emails.isEmpty() && isUsable(student.getEmail())) {
            missing.append("// Email ID: ").append(student.getEmail().strip()).append(newline);
        }
        if (missing.isEmpty()) {
            return false;
        }

        int bomLength = startsWithBom(original) ? UTF8_BOM.length : 0;
        ByteArrayOutputStream updated = new ByteArrayOutputStream(original.length + missing.length());
        updated.write(original, 0, bomLength);
        updated.writeBytes(missing.toString().getBytes(StandardCharsets.UTF_8));
        updated.write(original, bomLength, original.length - bomLength);
        Files.write(file, updated.toByteArray());
        return true;
    }

    private static Pattern fieldPattern(String label) {
        return Pattern.compile("^\\h*(?://|\\*)?\\h*" + label + "\\h*:\\h*([^\\r\\n]*)$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    private static boolean startsWithBom(byte[] content) {
        return content.length >= UTF8_BOM.length && content[0] == UTF8_BOM[0] && content[1] == UTF8_BOM[1] && content[2] == UTF8_BOM[2];
    }

    //a blank field is skipped > the template ships the section empty
    private static List<String> values(String header, Pattern pattern) {
        Matcher matcher = pattern.matcher(header);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1).strip();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    //comment text only > stops at the first java token
    private static String leadingComments(String source) {
        StringBuilder comments = new StringBuilder();
        int position = 0;
        while (position < source.length()) {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
            if (source.startsWith("//", position)) {
                int end = source.indexOf('\n', position);
                end = end < 0 ? source.length() : end;
                comments.append(source, position + 2, end).append('\n');
                position = end;
            } else if (source.startsWith("/*", position)) {
                int end = source.indexOf("*/", position + 2);
                if (end < 0) {
                    break;
                }
                comments.append(source, position + 2, end).append('\n');
                position = end + 2;
            } else {
                break;
            }
        }
        return comments.toString();
    }

    private static boolean conflicts(List<String> values, String expected) {
        return isUsable(expected) && values.stream().anyMatch(value -> !value.equalsIgnoreCase(expected.strip()));
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !value.contains("\r") && !value.contains("\n");
    }

    //#endregion

    //#region file helpers

    //an assessment path must stay inside root & never pass through a link
    private static Path resolveContained(Path root, String relative) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path name = Path.of(relative);
        Path result = base.resolve(name).normalize();
        if (name.isAbsolute() || !result.startsWith(base)) {
            throw new IOException("path escapes root > " + relative);
        }
        Path current = base;
        for (Path part : base.relativize(result)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("linked path > " + current);
            }
        }
        return result;
    }

    //@return > true when the name changed
    private static boolean rename(Path source, String expected) throws IOException {
        if (source.getFileName().toString().equals(expected)) {
            return false;
        }
        Path target = source.resolveSibling(expected);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSameFile(source, target)) {
            throw new IOException("normalization collision > destination already exists > " + target);
        }
        //intermediate name lets case only renames work on windows
        Path intermediate = source.resolveSibling(".normalize-" + UUID.randomUUID());
        Files.move(source, intermediate);
        Files.move(intermediate, target);
        return true;
    }

    //breadth first so a folder is always listed before anything inside it
    private static List<Path> searchableFolders(Path top) throws IOException {
        List<Path> folders = new ArrayList<>(List.of(top));
        for (int index = 0; index < folders.size(); index++) {
            Path folder = folders.get(index);
            if (folder.getNameCount() - top.getNameCount() < MAX_SEARCH_DEPTH) {
                folders.addAll(subfolders(folder));
            }
        }
        return folders;
    }

    private static List<Path> subfolders(Path folder) throws IOException {
        return visibleEntries(folder).stream().filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList();
    }

    private static List<Path> regularFiles(Path folder) throws IOException {
        return visibleEntries(folder).stream().filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
    }

    private static List<Path> visibleEntries(Path folder) throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> !path.getFileName().toString().equals(MAC_ARCHIVE_FOLDER)).sorted().toList();
        }
    }

    private static List<String> fileNames(List<Path> paths) {
        return paths.stream().map(path -> path.getFileName().toString()).toList();
    }

    private static void addIssue(List<SubmissionIssue> issues, List<QuestionDefinition> questions, String code,
            IssueSeverity severity, String message, Path path) {
        for (QuestionDefinition question : questions) {
            issues.add(normalizationIssue(code, severity, message, question.getId(), path));
        }
    }

    //#endregion
}

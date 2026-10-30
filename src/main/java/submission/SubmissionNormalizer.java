package submission;

import java.io.IOException;
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

    /**
     * only touches the extracted working copy > never the original zip
     * corrections become warnings so grading continues > anything that would need a guess becomes an error
     *
     * @param extracted > submission whose root directory is the freshly extracted zip
     * @param assessment > folders & files this submission should contain
     * @return > submission rooted at the normalized folder \ at the extracted folder if the layout cannot be resolved
     */
    public static StudentSubmission normalize(StudentSubmission extracted, AssessmentDefinition assessment) {
        Path extractedRoot = extracted.getRootDirectory().toAbsolutePath().normalize();
        Path root = extractedRoot;
        List<SubmissionIssue> issues = new ArrayList<>();

        try {
            Map<String, List<QuestionDefinition>> questions = groupByFolder(assessment);
            root = findAnswerRoot(extractedRoot, questions, extracted.getStudent(), assessment.getName());
            arrangeQuestionFolders(root, questions, issues);
            relocateStrayAnswers(extractedRoot, root, questions, issues);
            correctFileNames(root, questions, issues);
            root = moveToStudentFolder(extractedRoot, root, extracted.getStudent(), issues);
            addMissingHeaders(root, questions, extracted.getStudent(), issues);
        } catch (IOException | RuntimeException error) {
            issues.add(new SubmissionIssue("normalization error", IssueSeverity.ERROR, error.toString(), null, root));
        }

        StudentSubmission normalized = new StudentSubmission(extracted.getStudent(), extracted.getOriginalZip(), root);
        normalized.addIssues(extracted.getSubmissionIssues());
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

    //#region answer root

    //picks the folder recognising the most questions > shallowest wins so q1/q1/ does not beat its parent
    private static Path findAnswerRoot(Path extractedRoot, Map<String, List<QuestionDefinition>> questions,
            StudentDetails student, String templateFolder) throws IOException {
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
                .filter(folder -> !folder.getFileName().toString().equalsIgnoreCase(templateFolder)).toList();
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
        for (Path file : regularFiles(folder)) {
            String name = file.getFileName().toString();
            for (var entry : questions.entrySet()) {
                boolean isAnswer = entry.getValue().stream().flatMap(question -> question.getRequiredFiles().stream())
                        .anyMatch(name::equalsIgnoreCase);
                if (isAnswer) {
                    found.add(entry.getKey());
                }
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
            List<Path> folders = subfolders(root); //relisted because earlier questions may have renamed a folder
            List<Path> matches = withName(folders, expected);
            if (matches.isEmpty()) {
                matches = foldersHoldingOnly(expected, folders, questions);
            }
            if (matches.size() > 1) {
                addIssue(issues, entry.getValue(), "normalization collision", IssueSeverity.ERROR,
                        "multiple folders map to " + expected + " > " + fileNames(matches), root);
            } else if (matches.size() == 1) {
                Path folder = matches.getFirst();
                try {
                    if (rename(folder, expected)) {
                        addIssue(issues, entry.getValue(), "question folder normalized", IssueSeverity.WARNING,
                                folder.getFileName() + " > " + expected, root.resolve(expected));
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
            boolean namedForQuestion = questions.keySet().stream().anyMatch(name::equalsIgnoreCase);
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
                    List<Path> copies = filesNamed(folders, requiredFile);
                    if (copies.size() > 1) {
                        issues.add(new SubmissionIssue("ambiguous answer file", IssueSeverity.ERROR,
                                copies.size() + " copies of " + requiredFile + " found outside " + entry.getKey(), question.getId(), root));
                    } else if (copies.size() == 1) {
                        Path target = Files.createDirectories(questionFolder).resolve(requiredFile);
                        Files.move(copies.getFirst(), target);
                        issues.add(new SubmissionIssue("answer file relocated", IssueSeverity.WARNING,
                                extractedRoot.relativize(copies.getFirst()) + " > " + entry.getKey() + "/" + requiredFile, question.getId(), target));
                    }
                }
            }
        }
    }

    private static List<Path> filesNamed(List<Path> folders, String fileName) throws IOException {
        List<Path> matches = new ArrayList<>();
        for (Path folder : folders) {
            for (Path file : regularFiles(folder)) {
                if (file.getFileName().toString().equalsIgnoreCase(fileName)) {
                    matches.add(file);
                }
            }
        }
        return matches;
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
        List<Path> matches = filesNamed(List.of(folder), expected);
        if (matches.size() > 1) {
            throw new IOException("normalization collision > multiple files map to " + expected + " in " + folder);
        }
        if (matches.size() == 1 && rename(matches.getFirst(), expected)) {
            addIssue(issues, questions, "filename normalized", IssueSeverity.WARNING,
                    matches.getFirst().getFileName() + " > " + expected, folder.resolve(expected));
        }
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
            issues.add(new SubmissionIssue("submission root normalized", IssueSeverity.WARNING,
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
                                && StudentHeaderNormalizer.addMissingFields(file, student)) {
                            issues.add(new SubmissionIssue("student header normalized", IssueSeverity.WARNING,
                                    "added missing details from scoresheet > " + requiredFile, question.getId(), file));
                        }
                    } catch (IOException error) {
                        issues.add(new SubmissionIssue("normalization error", IssueSeverity.ERROR, error.toString(), question.getId(), file));
                    }
                }
            }
        }
    }

    //#region file helpers

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
            issues.add(new SubmissionIssue(code, severity, message, question.getId(), path));
        }
    }

    //#endregion
}

package submission;

import java.io.IOException;
import java.nio.file.Files;
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

/**
 * rearranges one extracted zip into <username>/<question folder>/<answer file> so grading finds every answer
 * fixes wrapper folders, misnamed folders, loose answers & wrong filename capitals
 * corrections become warnings > anything that would need a guess becomes an error
 */
public class SubmissionNormalizer {
    private static final int MAX_SEARCH_DEPTH = 8; //deepest wrapper folder chain to search

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
            normalized.add(Files.isDirectory(submission.getRootDirectory()) ? normalizeSubmission(submission, assessment) : submission);
        }
        return normalized;
    }

    private static StudentSubmission normalizeSubmission(StudentSubmission extracted, AssessmentDefinition assessment) {
        Path extractedRoot = extracted.getRootDirectory().toAbsolutePath().normalize();
        Path answerRoot = extractedRoot;
        Path root = extractedRoot;
        List<SubmissionIssue> issues = new ArrayList<>(extracted.getSubmissionIssues());

        try {
            Map<String, List<QuestionDefinition>> questions = groupByFolder(assessment);
            answerRoot = findAnswerRoot(extractedRoot, questions, extracted.getStudent(), assessment.getName());
            root = answerRoot;
            arrangeQuestionFolders(root, questions, issues);
            relocateStrayAnswers(extractedRoot, root, questions, issues);
            correctFileNames(root, questions, issues);
            root = moveToStudentFolder(extractedRoot, root, extracted.getStudent(), issues);
            issues.addAll(StudentHeaderNormalizer.fillMissingHeaders(root, assessment, extracted.getStudent()));
        } catch (IOException | RuntimeException error) {
            issues.add(new SubmissionIssue("normalization error", IssueSeverity.ERROR, error.toString(), null, root));
        }

        StudentSubmission normalized = new StudentSubmission(extracted.getStudent(), extracted.getOriginalZip(), root);
        normalized.addIssues(withMovedPaths(issues, answerRoot, root));
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
    private static Path findAnswerRoot(Path extractedRoot, Map<String, List<QuestionDefinition>> questions, StudentDetails student, String templateFolder) throws IOException {
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
            List<Path> named = candidates.stream().filter(folder -> folder.getFileName().toString().equalsIgnoreCase(student.getUsername())).toList();
            if (named.size() == 1) {
                return named.getFirst();
            }
        }
        //a working folder beside the untouched template is not ambiguous > any other tie would be a guess
        List<Path> withoutTemplate = candidates.stream().filter(folder -> !folder.getFileName().toString().equalsIgnoreCase(templateFolder)).toList();
        if (withoutTemplate.size() == 1) {
            return withoutTemplate.getFirst();
        }
        throw new IOException("ambiguous submission layout > " + candidates.size() + " folders hold the same answers");
    }

    private static Set<String> recognisedQuestions(Path folder, Map<String, List<QuestionDefinition>> questions) throws IOException {
        Set<String> recognised = foldersOfAnswersIn(folder, questions);
        String name = folder.getFileName().toString();
        //an answer saved under the wrong question must not make that folder look like the whole submission
        questions.keySet().stream().filter(name::equalsIgnoreCase).findFirst().ifPresent(ownFolder -> recognised.retainAll(Set.of(ownFolder)));

        for (String expected : questions.keySet()) {
            if (entriesNamed(folder, expected).stream().anyMatch(Files::isDirectory)) recognised.add(expected);
        }

        for (Path child : subfolders(folder)) {
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
        for (var entry : questions.entrySet()) {
            for (QuestionDefinition question : entry.getValue()) {
                for (String required : question.getRequiredFiles()) {
                    if (!filesNamed(List.of(folder), required).isEmpty()) found.add(entry.getKey());
                }
            }
        }
        return found;
    }

    //#endregion

    //#region question folders & files

    private static void arrangeQuestionFolders(Path root, Map<String, List<QuestionDefinition>> questions, List<SubmissionIssue> issues) throws IOException {
        for (var entry : questions.entrySet()) {
            String expected = entry.getKey();
            Path named;
            try {
                named = correctCase(root, expected, entry.getValue(), "question folder normalized", issues);
            } catch (IOException error) { //only this folder's questions are affected > the rest can still be graded
                addIssue(issues, entry.getValue(), "normalization error", IssueSeverity.ERROR, error.toString(), root);
                continue;
            }
            if (named != null && Files.isDirectory(named)) continue;
            List<Path> folders = subfolders(root); //relisted because earlier questions may have renamed a folder
            List<Path> matches = foldersAnsweringOnly(expected, folders, questions);
            if (matches.size() > 1) {
                addIssue(issues, entry.getValue(), "normalization collision", IssueSeverity.ERROR, "multiple folders map to " + expected + " > " + matches.stream().map(Path::getFileName).toList(), root);
            } else if (matches.size() == 1) {
                Path folder = matches.getFirst();
                Path destination = root.resolve(expected);
                try {
                    Files.move(folder, destination);
                    addIssue(issues, entry.getValue(), "question folder normalized", IssueSeverity.WARNING, folder.getFileName() + " > " + expected, destination);
                } catch (IOException error) {
                    addIssue(issues, entry.getValue(), "normalization error", IssueSeverity.ERROR, error.toString(), folder);
                }
            }
        }
    }

    private static List<Path> foldersAnsweringOnly(String expected, List<Path> folders, Map<String, List<QuestionDefinition>> questions) throws IOException {
        List<Path> matches = new ArrayList<>();
        for (Path folder : folders) {
            boolean namedForQuestion = questions.keySet().stream().anyMatch(folder.getFileName().toString()::equalsIgnoreCase);
            if (!namedForQuestion && foldersOfAnswersIn(folder, questions).equals(Set.of(expected))) {
                matches.add(folder);
            }
        }
        return matches;
    }

    //covers loose files & answers saved under the wrong question > a file already in place is never replaced
    private static void relocateStrayAnswers(Path extractedRoot, Path root, Map<String, List<QuestionDefinition>> questions, List<SubmissionIssue> issues) throws IOException {
        List<Path> folders = searchableFolders(extractedRoot);

        for (var entry : questions.entrySet()) {
            Path questionFolder = root.resolve(entry.getKey());

            for (QuestionDefinition question : entry.getValue()) {
                for (String requiredFile : question.getRequiredFiles()) {
                    if (!filesNamed(List.of(questionFolder), requiredFile).isEmpty()) {
                        continue;
                    }
                    List<Path> copies = unassignedFiles(filesNamed(folders, requiredFile), root, questions);
                    if (copies.size() > 1) {
                        issues.add(new SubmissionIssue("ambiguous answer file", IssueSeverity.ERROR, copies.size() + " copies of " + requiredFile + " found outside " + entry.getKey(), question.getId(), root));
                    } else if (copies.size() == 1) {
                        Path target = questionFolder.resolve(requiredFile);
                        Files.createDirectories(questionFolder);
                        Files.move(copies.getFirst(), target);
                        issues.add(new SubmissionIssue("answer file relocated", IssueSeverity.WARNING, extractedRoot.relativize(copies.getFirst()) + " > " + entry.getKey() + "/" + requiredFile, question.getId(), target));
                    }
                }
            }
        }
    }

    private static List<Path> filesNamed(List<Path> folders, String fileName) throws IOException {
        List<Path> matches = new ArrayList<>(); //not a set > windows paths compare equal across spellings
        for (Path folder : folders) {
            for (Path file : entriesNamed(folder, fileName)) {
                if (Files.isRegularFile(file)) matches.add(file);
            }
        }
        return matches;
    }

    private static List<Path> unassignedFiles(List<Path> files, Path root, Map<String, List<QuestionDefinition>> questions) throws IOException {
        Set<Path> assigned = new LinkedHashSet<>();
        for (var entry : questions.entrySet()) {
            for (Path folder : entriesNamed(root, entry.getKey())) {
                for (QuestionDefinition question : entry.getValue()) {
                    for (String required : question.getRequiredFiles()) {
                        assigned.addAll(entriesNamed(folder, required));
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
            for (QuestionDefinition question : entry.getValue()) {
                for (String required : question.getRequiredFiles()) {
                    try {
                        correctCase(folder, required, entry.getValue(), "filename normalized", issues);
                    } catch (IOException error) {
                        addIssue(issues, entry.getValue(), "normalization error", IssueSeverity.ERROR, error.toString(), folder);
                    }
                }
            }
        }
    }

    //@return > the corrected path \ null when nothing has that name
    private static Path correctCase(Path parent, String expected, List<QuestionDefinition> questions, String code, List<SubmissionIssue> issues) throws IOException {
        List<Path> matches = entriesNamed(parent, expected);
        if (matches.size() > 1) throw new IOException("normalization collision > multiple entries map to " + expected + " in " + parent);
        if (matches.isEmpty()) return null;

        Path actual = matches.getFirst();
        Path corrected = parent.resolve(expected);
        if (!actual.getFileName().toString().equals(expected)) {
            //intermediate name lets case only renames work on windows
            Path intermediate = actual.resolveSibling(".normalize-" + UUID.randomUUID());
            Files.move(actual, intermediate);
            Files.move(intermediate, corrected);
            addIssue(issues, questions, code, IssueSeverity.WARNING, actual.getFileName() + " > " + expected, corrected);
        }
        return corrected;
    }

    //searching keeps every spelling > only renaming a declared path treats two spellings as a collision
    private static List<Path> entriesNamed(Path parent, String name) throws IOException {
        if (!Files.isDirectory(parent)) return List.of();
        return visibleEntries(parent).stream().filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).toList();
    }

    //#endregion

    //the normalized folder is a sibling of the extraction > a wrapper named after the student can never be in the way
    private static Path moveToStudentFolder(Path extractedRoot, Path root, StudentDetails student, List<SubmissionIssue> issues) throws IOException {
        if (student == null) {
            return root;
        }
        Path normalizedParent = extractedRoot.resolveSibling(extractedRoot.getFileName() + "-normalized");
        Path target = Files.createDirectories(normalizedParent).resolve(student.getUsername());
        Files.move(root, target);

        if (!root.getFileName().toString().equals(student.getUsername())) {
            String original = root.equals(extractedRoot) ? "top level of zip" : extractedRoot.relativize(root).toString();
            issues.add(new SubmissionIssue("submission root normalized", IssueSeverity.WARNING, original + " > " + student.getUsername(), null, target));
        }
        return target;
    }

    private static List<SubmissionIssue> withMovedPaths(List<SubmissionIssue> issues, Path previousRoot, Path newRoot) {
        List<SubmissionIssue> moved = new ArrayList<>();
        for (SubmissionIssue issue : issues) {
            Path path = issue.getAffectedPath();
            if (path != null && path.toAbsolutePath().normalize().startsWith(previousRoot)) {
                path = newRoot.resolve(previousRoot.relativize(path.toAbsolutePath().normalize()));
            }
            moved.add(new SubmissionIssue(issue.getCode(), issue.getSeverity(), issue.getMessage(), issue.getQuestionId(), path));
        }
        return moved;
    }

    //#region file helpers

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
        return visibleEntries(folder).stream().filter(Files::isDirectory).toList();
    }

    private static List<Path> visibleEntries(Path folder) throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(path -> !path.getFileName().toString().startsWith(".")).sorted().toList();
        }
    }

    private static void addIssue(List<SubmissionIssue> issues, List<QuestionDefinition> questions, String code, IssueSeverity severity, String message, Path path) {
        for (QuestionDefinition question : questions) {
            issues.add(new SubmissionIssue(code, severity, message, question.getId(), path));
        }
    }

    //#endregion
}

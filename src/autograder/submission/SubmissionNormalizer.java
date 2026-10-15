package autograder.submission;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** AI-assisted with Codex: selects roots and corrects unambiguous names in extracted working copies. */
public class SubmissionNormalizer {
    public static StudentSubmission normalize(StudentSubmission submission, AssessmentDefinition assessment) {
        return normalize(submission, assessment, Map.of());
    }

    /** Alias keys are submitted folder names; values are assessment folder names. */
    public static StudentSubmission normalize(StudentSubmission submission, AssessmentDefinition assessment,
            Map<String, String> folderAliases) {
        if (!submission.getQuestionAttempts().isEmpty()) {
            throw new IllegalStateException("normalize before validation");
        }
        List<SubmissionIssue> issues = new ArrayList<>();
        try {
            if (submission.getStudent() == null) {
                throw new IOException("cannot normalize without a matched student");
            }
            Path extracted = submission.getRootDirectory().toAbsolutePath().normalize();
            if (!Files.isDirectory(extracted, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("extracted submission directory is missing > " + extracted);
            }
            Map<String, List<QuestionDefinition>> questions = groupQuestions(assessment);
            Map<String, String> aliases = validateAliases(folderAliases, questions.keySet());
            Path root = findRoot(extracted, submission.getStudent().getUserName(), questions, aliases);
            submission.setRootDirectory(root);
            if (!root.getFileName().toString().equalsIgnoreCase(submission.getStudent().getUserName())) {
                issues.add(new SubmissionIssue("submission root normalized", IssueSeverity.WARNING,
                        "selected working root > " + root + " (extracted at " + extracted + ")", null, root));
            }

            Map<String, List<Path>> sources = new LinkedHashMap<>();
            for (Path folder : directories(root)) {
                Set<String> matches = matchFolder(folder, questions, aliases);
                if (matches.size() > 1) {
                    for (String match : matches) {
                        folderIssue(issues, questions.get(match), "ambiguous question folder", IssueSeverity.ERROR,
                                "folder contains answer files for multiple questions > " + folder, folder);
                    }
                } else if (matches.size() == 1) {
                    sources.computeIfAbsent(matches.iterator().next(), key -> new ArrayList<>()).add(folder);
                }
            }
            for (var entry : sources.entrySet()) {
                List<QuestionDefinition> definitions = questions.get(entry.getKey());
                if (entry.getValue().size() > 1) {
                    folderIssue(issues, definitions, "normalization collision", IssueSeverity.ERROR,
                            "multiple folders map to " + entry.getKey() + " > " + entry.getValue(), root);
                    continue;
                }
                Path folder = entry.getValue().get(0);
                try {
                    Path normalized = rename(folder, entry.getKey(), extracted);
                    if (!normalized.getFileName().toString().equals(folder.getFileName().toString())) {
                        folderIssue(issues, definitions, "question folder normalized", IssueSeverity.WARNING,
                                folder + " -> " + normalized, normalized);
                    }
                    normalizeFiles(normalized, definitions, extracted, submission, issues);
                } catch (IOException error) {
                    folderIssue(issues, definitions, "normalization error", IssueSeverity.ERROR,
                            error.toString(), folder);
                }
            }
        } catch (IOException | RuntimeException error) {
            issues.add(new SubmissionIssue("normalization error", IssueSeverity.ERROR,
                    error.toString(), null, submission.getRootDirectory()));
        }
        submission.addIssues(issues);
        return submission;
    }

    private static Map<String, List<QuestionDefinition>> groupQuestions(AssessmentDefinition assessment) throws IOException {
        Map<String, List<QuestionDefinition>> result = new LinkedHashMap<>();
        Map<String, String> spelling = new LinkedHashMap<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            String folder = question.getExpectedFolder();
            checkName(folder);
            String previous = spelling.putIfAbsent(folder.toLowerCase(Locale.ROOT), folder);
            if (previous != null && !previous.equals(folder)) {
                throw new IOException("assessment folder names differ only by case > " + previous + ", " + folder);
            }
            result.computeIfAbsent(folder, key -> new ArrayList<>()).add(question);
        }
        return result;
    }

    private static Map<String, String> validateAliases(Map<String, String> supplied, Set<String> expected) throws IOException {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (var entry : supplied.entrySet()) {
            checkName(entry.getKey());
            if (!expected.contains(entry.getValue())) {
                throw new IOException("folder alias has no assessment target > " + entry);
            }
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String previous = aliases.putIfAbsent(key, entry.getValue());
            if ((previous != null && !previous.equals(entry.getValue()))
                    || expected.stream().anyMatch(name -> name.equalsIgnoreCase(entry.getKey()) && !name.equals(entry.getValue()))) {
                throw new IOException("conflicting folder alias > " + entry);
            }
        }
        return aliases;
    }

    private static Path findRoot(Path extracted, String username, Map<String, List<QuestionDefinition>> questions,
            Map<String, String> aliases) throws IOException {
        List<Path> candidates = new ArrayList<>();
        if (hasQuestions(extracted, questions, aliases)) {
            candidates.add(extracted);
        }
        for (Path folder : directories(extracted)) {
            String name = folder.getFileName().toString();
            if (name.equalsIgnoreCase(username) || name.equalsIgnoreCase("RenameToYourUsername")
                    || hasQuestions(folder, questions, aliases)) {
                candidates.add(folder);
            }
        }
        if (candidates.size() != 1) {
            throw new IOException((candidates.isEmpty() ? "unknown" : "ambiguous") + " submission layout > " + extracted);
        }
        return candidates.get(0);
    }

    private static boolean hasQuestions(Path root, Map<String, List<QuestionDefinition>> questions,
            Map<String, String> aliases) throws IOException {
        for (Path folder : directories(root)) {
            if (!matchFolder(folder, questions, aliases).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> matchFolder(Path folder, Map<String, List<QuestionDefinition>> questions,
            Map<String, String> aliases) throws IOException {
        String name = folder.getFileName().toString();
        for (String expected : questions.keySet()) {
            if (expected.equalsIgnoreCase(name)) {
                return Set.of(expected);
            }
        }
        String alias = aliases.get(name.toLowerCase(Locale.ROOT));
        if (alias != null) {
            return Set.of(alias);
        }
        Set<String> files = new LinkedHashSet<>();
        for (Path child : children(folder)) {
            if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                files.add(child.getFileName().toString().toLowerCase(Locale.ROOT));
            }
        }
        Set<String> matches = new LinkedHashSet<>();
        for (var entry : questions.entrySet()) {
            if (entry.getValue().stream().flatMap(question -> question.getRequiredFiles().stream())
                    .anyMatch(file -> files.contains(file.toLowerCase(Locale.ROOT)))) {
                matches.add(entry.getKey());
            }
        }
        return matches;
    }

    private static void normalizeFiles(Path folder, List<QuestionDefinition> questions, Path extracted,
            StudentSubmission submission, List<SubmissionIssue> issues) throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        for (QuestionDefinition question : questions) {
            List<String> names = new ArrayList<>(question.getRequiredFiles());
            names.addAll(question.getAllowedResourceFiles());
            for (String name : names) {
                checkName(name);
                String previous = expected.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                if (previous != null && !previous.equals(name)) {
                    throw new IOException("assessment filenames differ only by case > " + previous + ", " + name);
                }
            }
        }
        Map<String, List<Path>> matches = new LinkedHashMap<>();
        for (Path file : children(folder)) {
            String canonical = expected.get(file.getFileName().toString().toLowerCase(Locale.ROOT));
            if (canonical != null) {
                matches.computeIfAbsent(canonical, key -> new ArrayList<>()).add(file);
            }
        }
        if (matches.values().stream().anyMatch(paths -> paths.size() > 1)) {
            throw new IOException("normalization collision > multiple files map to the same expected filename in " + folder);
        }
        for (var entry : matches.entrySet()) {
            Path file = entry.getValue().get(0);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("expected answer/resource is not a regular file > " + file);
            }
            Path normalized = rename(file, entry.getKey(), extracted);
            if (!normalized.getFileName().toString().equals(file.getFileName().toString())) {
                folderIssue(issues, questions, "filename normalized", IssueSeverity.WARNING,
                        file + " -> " + normalized, normalized);
            }
            for (QuestionDefinition question : questions) {
                if (question.getRequiredFiles().contains(entry.getKey()) && entry.getKey().endsWith(".java")) {
                    try {
                        issues.addAll(StudentHeaderNormalizer.normalize(normalized, submission.getStudent(), question.getId()));
                    } catch (IOException error) {
                        issues.add(new SubmissionIssue("normalization error", IssueSeverity.ERROR,
                                error.toString(), question.getId(), normalized));
                    }
                }
            }
        }
    }

    private static Path rename(Path source, String expected, Path extracted) throws IOException {
        checkName(expected);
        Path boundary = extracted.toRealPath();
        Path parent = source.getParent().toRealPath();
        Path target = parent.resolve(expected);
        if (!parent.startsWith(boundary) || Files.isSymbolicLink(source)) {
            throw new IOException("rename escapes the extracted working copy > " + source);
        }
        if (source.getFileName().toString().equals(expected)) {
            return source;
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSameFile(source, target)) {
            throw new IOException("normalization collision > destination already exists: " + target);
        }
        //The intermediate name makes case-only renames work on Windows too. Neither move overwrites a target.
        Path temporary = parent.resolve(".normalize-" + UUID.randomUUID());
        Files.move(source, temporary);
        try {
            return Files.move(temporary, target);
        } catch (IOException error) {
            try {
                Files.move(temporary, source);
            } catch (IOException rollback) {
                error.addSuppressed(rollback);
            }
            throw error;
        }
    }

    private static void checkName(String name) throws IOException {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || Path.of(name).isAbsolute()) {
            throw new IOException("expected a simple folder or filename > " + name);
        }
    }

    private static List<Path> children(Path root) throws IOException {
        try (Stream<Path> paths = Files.list(root)) {
            List<Path> result = paths.sorted().toList();
            for (Path path : result) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links cannot be normalized safely > " + path);
                }
            }
            return result;
        }
    }

    private static List<Path> directories(Path root) throws IOException {
        return children(root).stream().filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> !path.getFileName().toString().equalsIgnoreCase("__MACOSX")).toList();
    }

    private static void folderIssue(List<SubmissionIssue> issues, List<QuestionDefinition> questions,
            String code, IssueSeverity severity, String message, Path path) {
        for (QuestionDefinition question : questions) {
            issues.add(new SubmissionIssue(code, severity, message, question.getId(), path));
        }
    }
}

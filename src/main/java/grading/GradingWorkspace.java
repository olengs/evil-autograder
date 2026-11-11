package grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import submission.QuestionAttempt;
import submission.files.SubmissionFileFinder;
import utils.files.FileUtils;
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;

public class GradingWorkspace implements AutoCloseable { //reconciles answer, support & tester files inside the student's question directory
    private final Path directory; //the student's extracted question directory > graded in place

    //track changes so an in-place question directory can be restored on close
    private final Set<Path> addedFiles = new LinkedHashSet<>();          //files this workspace newly created
    private final Set<Path> addedDirectories = new LinkedHashSet<>();    //directories this workspace newly created
    private final Map<Path, byte[]> overwrittenFiles = new LinkedHashMap<>(); //original bytes of files this workspace replaced
    private Path classesDirectory; //temp dir for compiled .class output, created inside the question directory

    public GradingWorkspace(Path questionDirectory) throws IOException {
        //normalize to a real, absolute base so path-safety checks & relativization are consistent
        this.directory = questionDirectory.toRealPath();
    }

    public void prepare(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester) throws IOException {
        var finder = new SubmissionFileFinder();
        Map<String, Path> answerFiles = locateRequiredAnswers(question, attempt, finder);
        List<Path> submissionFolders = findSubmissionFolders(attempt, answerFiles);

        //copy order matters > supplied support & student answers & missing data > then tester resources
        copySupportFiles(question, submissionFolders, finder);
        copyAnswerFiles(answerFiles);
        copyAdditionalSubmissionData(submissionFolders);
        copyTesterResources(question, tester);
    }

    private Map<String, Path> locateRequiredAnswers(QuestionDefinition question, QuestionAttempt attempt, SubmissionFileFinder finder) throws IOException {
        Map<String, Path> answerFiles = new LinkedHashMap<>();
        for (String requiredAnswer : question.getRequiredAnswerFiles()) {
            List<Path> matches = attempt.getSubmittedFiles().stream().filter(path -> finder.matchesRequiredPath(path, requiredAnswer)).distinct().toList();
           
            if (matches.size() > 1) throw new IOException("ambiguous answer file > " + requiredAnswer);
            Path answerFile;

            if (matches.isEmpty()) answerFile = finder.findFileIgnoringCase(attempt.getQuestionDirectory(), requiredAnswer) .orElseThrow(() -> new IOException("missing required answer > " + requiredAnswer)); else {
                answerFile = matches.getFirst();
            }

            answerFiles.put(requiredAnswer, answerFile);
        }
        return answerFiles;
    }

    private List<Path> findSubmissionFolders(QuestionAttempt attempt, Map<String, Path> answerFiles) throws IOException {
        List<Path> folders = new ArrayList<>();
        if (Files.isDirectory(attempt.getQuestionDirectory())) folders.add(attempt.getQuestionDirectory().toRealPath());

        //include relocated answer folders so helpers & data remain available
        for (Path answerFile : answerFiles.values()) folders.add(answerFile.getParent().toRealPath());
        return folders.stream().distinct().toList();
    }

    private void copySupportFiles(QuestionDefinition question, List<Path> submissionFolders, SubmissionFileFinder finder) throws IOException {
        for (String supportFile : question.getSupportFiles()) {
            Path source = question.getResourceDirectory().resolve(supportFile);
            //a declared java helper may be student work > supplied data & bytecode stay authoritative
            if (supportFile.endsWith(".java")) {
                List<Path> studentHelpers = new ArrayList<>();
                for (Path folder : submissionFolders) {
                    finder.findFileIgnoringCase(folder, supportFile).ifPresent(studentHelpers::add);
                }

                studentHelpers = studentHelpers.stream().distinct().toList();
                if (studentHelpers.size() > 1) throw new IOException("ambiguous support file > " + supportFile);
                if (!studentHelpers.isEmpty()) source = studentHelpers.getFirst();
            }
            copyFile(source, supportFile);
        }
    }

    private void copyAnswerFiles(Map<String, Path> answerFiles) throws IOException {
        //correct spelling only in workspace > extracted filenames & source contents stay untouched
        for (var answer : answerFiles.entrySet()) copyFile(answer.getValue(), answer.getKey());
    }

    private void copyAdditionalSubmissionData(List<Path> submissionFolders) throws IOException {
        for (Path folder : submissionFolders) {
            try (var files = Files.walk(folder)) {
                for (Path source : files.filter(Files::isRegularFile).toList()) {
                    String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.startsWith(".") || name.endsWith(".java") || name.endsWith(".class")) continue;
                    String relativeName = folder.relativize(source).toString();
                    //student data fills gaps without replacing supplied support files
                    if (!Files.exists(getDirectory().resolve(relativeName))) copyFile(source, relativeName);
                }
            }
        }
    }

    private void copyTesterResources(QuestionDefinition question, TesterDefinition tester) throws IOException {
        Path testerFile = tester.getSourceFile();
        Path testerFolder = testerFile.getParent();
        try (var entries = Files.walk(testerFolder)) {
            for (Path source : entries.toList()) {
                Path relativePath = testerFolder.relativize(source);
                Path destination = getDirectory().resolve(relativePath);

                if (Files.isDirectory(source)) {
                    if (!Files.exists(destination)) addedDirectories.add(destination.normalize());
                    Files.createDirectories(destination);
                    continue;
                }
                if (source.getFileName().toString().endsWith("Tester.java") && !source.equals(testerFile)) {
                    continue;
                }

                //tester resources have final priority > cannot replace required student answers
                boolean isRequiredAnswer = question.getRequiredAnswerFiles().stream().anyMatch(name -> Path.of(name).normalize().toString().equalsIgnoreCase(relativePath.toString()));
                if (isRequiredAnswer) continue;
                copyFile(source, relativePath.toString());
            }
        }
    }

    private void copyFile(Path source, String relativeName) throws IOException {
        Path destination = getDirectory().resolve(relativeName).normalize();
        if (!destination.startsWith(getDirectory()) || destination.equals(getDirectory())) throw new IOException("invalid workspace path > " + relativeName);

        //in-place grading: the file is already correctly placed > nothing to copy or track
        if (Files.exists(destination) && Files.isSameFile(source, destination)) return;

        //record how to undo this change before overwriting > newly added files are deleted, replaced files are restored
        if (Files.exists(destination)) {
            if (!overwrittenFiles.containsKey(destination) && !addedFiles.contains(destination)) {
                overwrittenFiles.put(destination, Files.readAllBytes(destination));
            }
        } else if (!overwrittenFiles.containsKey(destination)) {
            addedFiles.add(destination);
        }

        FileUtils.copyFileReplacing(source, destination);
    }

    public Path getDirectory() {
        return directory;
    }

    //compiled .class output lives in a temp dir inside the question directory & is removed on close
    public Path createClassesDirectory() throws IOException {
        if (classesDirectory == null) {
            classesDirectory = Files.createTempDirectory(directory, "autograder-classes-");
        }
        return classesDirectory;
    }

    @Override
    public void close() throws IOException {
        // Restore the student's question directory to its pre-grading state.
        // Best-effort: a cleanup failure is logged but never masks a grading exception.
        List<String> failures = new ArrayList<>();

        //remove the compiled-classes temp dir (and its .class files) created inside the question directory
        if (classesDirectory != null) {
            try {
                if (Files.isDirectory(classesDirectory)) FileUtils.deleteDirectory(classesDirectory);
            } catch (IOException error) {
                failures.add("delete classes dir " + classesDirectory + " > " + error.getMessage());
            }
            classesDirectory = null;
        }

        //restore files this workspace overwrote, back to their original bytes
        for (var entry : overwrittenFiles.entrySet()) {
            try {
                Files.write(entry.getKey(), entry.getValue());
            } catch (IOException error) {
                failures.add("restore " + entry.getKey() + " > " + error.getMessage());
            }
        }

        //delete files this workspace newly created (injected tester source & resources, relocated data)
        for (Path added : addedFiles) {
            try {
                Files.deleteIfExists(added);
            } catch (IOException error) {
                failures.add("delete " + added + " > " + error.getMessage());
            }
        }

        //remove directories this workspace created, deepest-first & only when empty
        List<Path> directoriesDeepestFirst = new ArrayList<>(addedDirectories);
        directoriesDeepestFirst.sort(java.util.Comparator.comparingInt(Path::getNameCount).reversed());
        for (Path dir : directoriesDeepestFirst) {
            try {
                if (Files.isDirectory(dir)) Files.deleteIfExists(dir); //deleteIfExists on a dir fails if non-empty > left for run-end cleanup
            } catch (IOException error) {
                failures.add("remove dir " + dir + " > " + error.getMessage());
            }
        }

        overwrittenFiles.clear();
        addedFiles.clear();
        addedDirectories.clear();

        if (!failures.isEmpty()) {
            JsonLineLogger.getInstance().append(Status.WARNING, "GradingWorkspace",
                    "in-place cleanup incomplete for " + directory + " > " + String.join("; ", failures));
        }
    }
}

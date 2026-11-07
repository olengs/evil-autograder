//prepares & owns temporary files used to compile & run one question
//copies student answers & support files & tester resources in order > then deletes workspace on close

package grading;

import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import utils.files.FileUtils;
import utils.files.TemporaryDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import submission.QuestionAttempt;
import submission.files.SubmissionFileFinder;

public class GradingWorkspace implements AutoCloseable {
    private final TemporaryDirectory temporaryDirectory;

    public GradingWorkspace() throws IOException {
        temporaryDirectory = new TemporaryDirectory("autograder-ws-");
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
                    Files.createDirectories(destination);
                    continue;
                }
                if (source.getFileName().toString().endsWith("Tester.java") && !source.equals(testerFile)) {
                    continue;
                }

                //tester resources have final priority > cannot replace required student answers
                boolean isRequiredAnswer = question.getRequiredAnswerFiles().stream().anyMatch(name -> Path.of(name).normalize().toString().equalsIgnoreCase(relativePath.toString()));
                if (isRequiredAnswer) continue;
                FileUtils.copyFileReplacing(source, destination);
            }
        }
    }

    private void copyFile(Path source, String relativeName) throws IOException {
        Path destination = getDirectory().resolve(relativeName).normalize();
        if (!destination.startsWith(getDirectory()) || destination.equals(getDirectory())) throw new IOException("invalid workspace path > " + relativeName);
        Files.createDirectories(destination.getParent());
        FileUtils.copyFileReplacing(source, destination);
    }

    public Path getDirectory() {
        return temporaryDirectory.path();
    }

    @Override
    public void close() throws IOException {
        temporaryDirectory.close();
    }
}

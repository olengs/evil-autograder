package generation.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import generation.questionSpec.QuestionSpec;
import utils.FileUtils;

public class TesterWriter { //writes tester to the output folder > backs up any existing one first
    // backups live in a sub-folder so the testers folder stays clean for TesterLoader's *Tester.java glob
    private static final String BACKUP_FOLDER = "backup";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // the files bundle sent in the prompt > not *Tester.java so TesterLoader ignores it
    private static final String FILES_BUNDLE = "files.md";

    // write the files bundle next to the testers so the exact llm context can be inspected > replaced every run
    public Path writeFilesBundle(String files, Path outputDirectory) throws IOException {
        Path directory = Files.createDirectories(outputDirectory.toAbsolutePath().normalize());
        return Files.writeString(directory.resolve(FILES_BUNDLE), files);
    }

    // write the generated tester > return the path written > never touches answer templates
    public Path write(QuestionSpec spec, String testerSource, Path outputDirectory) throws IOException {
        if (testerSource == null || testerSource.isBlank()) {
            throw new IOException("refusing to write empty tester for " + spec.getId());
        }
        Path directory = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory); // create the output folder when missing

        Path target = directory.resolve(spec.getTesterFileName());

        // preserve any existing tester (e.g. a hand-written one) before overwriting
        if (Files.exists(target)) {
            backup(target, directory);
        }

        Files.writeString(target, testerSource);
        return target;
    }

    // copy the existing tester into backup/<name>.<timestamp>.bak before it is replaced
    private void backup(Path existingTester, Path directory) throws IOException {
        Path backupDirectory = Files.createDirectories(directory.resolve(BACKUP_FOLDER));
        String stamped = existingTester.getFileName().toString() + "." + LocalDateTime.now().format(STAMP) + ".bak";
        Path backupFile = backupDirectory.resolve(stamped);
        FileUtils.copyFile(existingTester, backupFile, FileUtils.CopyOptions.REPLACE_EXISTING);
    }
}

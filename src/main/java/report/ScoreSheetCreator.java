//creates a roster scoresheet from submission zip names & student headers when none is supplied
//provides student identities for grading while leaving any existing scoresheet untouched

package report;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import submission.SubmissionArchives;
import submission.SubmissionFileNames;
import submission.files.StudentHeaderReader;

public final class ScoreSheetCreator {
    private static final String defaultEmailDomain = "computing.smu.edu.sg";

    private ScoreSheetCreator() {}

    public static boolean createIfMissing(Path scoresheet, Path submissionsFolder) throws IOException {
        checkInterrupted();
        if (Files.exists(scoresheet)) return false;

        List<String[]> rows = new ArrayList<>();
        rows.add(ScoreSheetColumns.header());
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Path zip : SubmissionArchives.listZipFiles(submissionsFolder)) {
            checkInterrupted();
            String username = SubmissionFileNames.username(zip);
            if (usernames.add(username)) rows.add(studentRow(zip, username));
        }

        CsvWriter.write(scoresheet, rows);
        return true;
    }

    private static String[] studentRow(Path zip, String username) throws InterruptedIOException {
        StudentHeaderReader.Header header = StudentHeaderReader.readFields(readJavaSources(zip));
        String email = header.email();
        if (!email.toLowerCase(Locale.ROOT).startsWith(username.toLowerCase(Locale.ROOT) + "@")) {
            email = username + "@" + defaultEmailDomain;
        }
        
        String[] row = new String[ScoreSheetColumns.endOfLine + 1];
        Arrays.fill(row, "");
        row[ScoreSheetColumns.username] = username;
        row[ScoreSheetColumns.firstName] = header.name();
        row[ScoreSheetColumns.email] = email;
        return row;
    }

    private static String readJavaSources(Path zip) throws InterruptedIOException {
        checkInterrupted();
        StringBuilder sources = new StringBuilder();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            char[] buffer = new char[8192];
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                checkInterrupted();
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".java")) {
                    try (var source = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) {
                        while (true) {
                            checkInterrupted();
                            int count = source.read(buffer);
                            if (count == -1) break;
                            checkInterrupted();
                            sources.append(buffer, 0, count);
                        }
                        sources.append('\n');
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            if (error instanceof InterruptedIOException interrupted) throw interrupted;
            checkInterrupted();
            //a broken zip still gets a roster row > submissionloader reports extraction error later
        }
        checkInterrupted();
        return sources.toString();
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("scoresheet creation interrupted");
    }
}

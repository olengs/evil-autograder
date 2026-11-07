//creates a roster scoresheet from submission zip names & student headers when none is supplied
//provides student identities for grading while leaving any existing scoresheet untouched

package report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        if (Files.exists(scoresheet)) return false;

        List<String[]> rows = new ArrayList<>();
        rows.add(ScoreSheetColumns.header());
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Path zip : SubmissionArchives.listZipFiles(submissionsFolder)) {
            String username = SubmissionFileNames.username(zip);
            if (usernames.add(username)) rows.add(studentRow(zip, username));
        }

        CsvWriter.write(scoresheet, rows);
        return true;
    }

    private static String[] studentRow(Path zip, String username) {
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

    private static String readJavaSources(Path zip) {
        StringBuilder sources = new StringBuilder();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : Collections.list(file.entries())) {
                if (entry.getName().endsWith(".java")) {
                    try (var source = file.getInputStream(entry)) {
                        sources.append(new String(source.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            //a broken zip still gets a roster row > submissionloader reports extraction error later
        }
        return sources.toString();
    }
}

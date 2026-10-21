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
import utils.files.CsvWriter;
import utils.process.Interruptions;

public final class ScoreSheetCreator { //creates a missing student scoresheet from ZIP names & source headers
    private static final String defaultEmailDomain = "computing.smu.edu.sg";

    private ScoreSheetCreator() {}

    public static boolean createIfMissing(Path scoresheet, Path submissionsFolder) throws IOException {
        Interruptions.check("scoresheet creation interrupted");
        if (Files.exists(scoresheet)) return false;

        List<String[]> rows = new ArrayList<>();
        rows.add(ScoreSheetColumns.header());
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Path zip : SubmissionArchives.listZipFiles(submissionsFolder)) {
            Interruptions.check("scoresheet creation interrupted");
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
        
        String[] row = new String[ScoreSheetColumns.header().length];
        Arrays.fill(row, "");
        row[ScoreSheetColumns.username] = username;
        row[ScoreSheetColumns.firstName] = header.name();
        row[ScoreSheetColumns.email] = email;
        return row;
    }

    private static String readJavaSources(Path zip) throws InterruptedIOException {
        Interruptions.check("scoresheet creation interrupted");
        StringBuilder sources = new StringBuilder();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            char[] buffer = new char[8192];
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                Interruptions.check("scoresheet creation interrupted");
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".java")) {
                    try (var source = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) {
                        while (true) {
                            Interruptions.check("scoresheet creation interrupted");
                            int count = source.read(buffer);
                            if (count == -1) break;
                            Interruptions.check("scoresheet creation interrupted");
                            sources.append(buffer, 0, count);
                        }
                        sources.append('\n');
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            if (error instanceof InterruptedIOException interrupted) throw interrupted;
            Interruptions.check("scoresheet creation interrupted");
            //a broken zip still gets a roster row > submissionloader reports extraction error later
        }
        Interruptions.check("scoresheet creation interrupted");
        return sources.toString();
    }
}

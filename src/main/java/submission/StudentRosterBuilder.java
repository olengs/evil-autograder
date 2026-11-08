package submission;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import submission.files.StudentHeaderReader;
import utils.process.Interruptions;
import utils.strings.CommonPatternChecker;

public class StudentRosterBuilder {
    
    public static List<StudentDetails> build(Path submissionsFolder) throws IOException {
        ArrayList<StudentDetails> studentDetails = new ArrayList<>();

        for (Path zip : SubmissionArchives.listZipFiles(submissionsFolder)) {
            Interruptions.check("scoresheet creation interrupted");
            String username = SubmissionFileNames.username(zip);
            if (!CommonPatternChecker.ofUsernamePattern(username)) {
                username = "";
            }

            StudentHeaderReader.Header header = StudentHeaderReader.readFields(readJavaSources(zip));
            String email = header.email().strip();
            int atIndex = email.indexOf('@');
            if (atIndex >= 0) {
                email = email.substring(0, atIndex).strip();
            }
            if (email.isBlank()) {
                email = username;
            }

            StudentDetails student = new StudentDetails(header.name(), username, email);
            studentDetails.add(student);
        }
        
        return studentDetails;
        
    }

    
    private static String readJavaSources(Path zip) throws InterruptedIOException {
        Interruptions.check("roster builder interrupted");
        StringBuilder sources = new StringBuilder();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                Interruptions.check("roster builder interrupted");
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".java")) {
                    try (var reader = new BufferedReader(
                            new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8))) {
                        
                        int linesRead = 0;
                        String line;
                        while (linesRead < 16 && (line = reader.readLine()) != null) {
                            Interruptions.check("roster builder interrupted");
                            sources.append(line).append('\n');
                            linesRead++;
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            if (error instanceof InterruptedIOException interrupted) throw interrupted;
            // a broken zip still tries to get student details, program should report extraction error later
        }
        Interruptions.check("roster builder interrupted");
        return sources.toString();
    }
}

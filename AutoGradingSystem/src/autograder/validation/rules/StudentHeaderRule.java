package autograder.validation.rules;

import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.Student;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.AnswerFileLocator;
import autograder.validation.ValidationRule;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentHeaderRule
        implements ValidationRule {

    private static final int HEADER_LINES_TO_CHECK = 15;

    private final AnswerFileLocator fileLocator;

    public StudentHeaderRule(
            AnswerFileLocator fileLocator) {

        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(
            StudentSubmission submission,
            QuestionDefinition question) {

        List<SubmissionIssue> issues =
                new ArrayList<>();

        Path questionFolder =
                submission
                        .getRootDirectory()
                        .resolve(
                            question
                                .getExpectedFolder());

        if (!Files.isDirectory(
                questionFolder)) {

            return issues;
        }

        Student student =
                submission.getStudent();

        for (String requiredFile :
                question.getRequiredFiles()) {

            try {

                Optional<Path> file =
                        fileLocator.findExact(
                                questionFolder,
                                requiredFile);

                /*
                 * If filename itself is invalid,
                 * naming validation deals with it.
                 *
                 * For now, inspect only correctly
                 * named source files.
                 */
                if (file.isEmpty()) {
                    continue;
                }

                String header =
                        readHeader(
                                file.get());

                String lowerHeader =
                        header.toLowerCase();

                boolean containsName =
                        student.getName() != null
                        && !student.getName()
                                   .isBlank()
                        && lowerHeader.contains(
                                student
                                    .getName()
                                    .toLowerCase());

                boolean containsEmail =
                        student.getEmail() != null
                        && !student.getEmail()
                                   .isBlank()
                        && lowerHeader.contains(
                                student
                                    .getEmail()
                                    .toLowerCase());

                if (!containsName
                        || !containsEmail) {

                    issues.add(
                            new SubmissionIssue(
                                    "MISSING_STUDENT_HEADER",
                                    IssueSeverity.WARNING,
                                    "Student name/email "
                                    + "was not found near "
                                    + "the top of "
                                    + requiredFile
                                    + ".",
                                    question.getId(),
                                    file.get()
                            )
                    );
                }

            } catch (IOException e) {

                issues.add(
                        new SubmissionIssue(
                                "FILE_READ_ERROR",
                                IssueSeverity.ERROR,
                                "Unable to read "
                                        + requiredFile
                                        + ".",
                                question.getId(),
                                questionFolder
                                    .resolve(
                                        requiredFile)
                        )
                );
            }
        }

        return issues;
    }

    private String readHeader(
            Path file)
            throws IOException {

        StringBuilder header =
                new StringBuilder();

        try (BufferedReader reader =
                     Files.newBufferedReader(
                             file)) {

            String line;
            int count = 0;

            while ((line =
                    reader.readLine()) != null
                    && count
                    < HEADER_LINES_TO_CHECK) {

                header.append(line)
                      .append(
                          System.lineSeparator());

                count++;
            }
        }

        return header.toString();
    }
}
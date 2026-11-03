// Associates an original ZIP with its extracted working directory.
// Carries both paths between loading and validation.

package autograder.submission;

import java.nio.file.Path;

public record ExtractedSubmission(Path originalZip, Path rootDirectory) {}

// Defines filename conventions for student ZIP submissions.
// Recognizes ZIP suffixes and builds stable extraction names.

package autograder.submission;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class SubmissionFileNames {
    private static final String ZIP_EXTENSION = ".zip";

    private SubmissionFileNames() {}

    public static boolean hasZipExtension(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION);
    }

    /** Callers supply a ZIP filename; academic-year prefixes remain part of its stem. */
    public static String baseNameWithoutZipExtension(Path zipFile) {
        String name = zipFile.getFileName().toString();
        return name.substring(0, name.length() - ZIP_EXTENSION.length());
    }

    public static String extractionFolderName(Path zipFile) throws IOException {
        String baseName = baseNameWithoutZipExtension(zipFile);
        if (baseName.isEmpty() || baseName.equals(".") || baseName.equals("..")) {
            throw new IOException("invalid submission filename > " + zipFile.getFileName());
        }
        return baseName.toLowerCase(Locale.ROOT);
    }
}

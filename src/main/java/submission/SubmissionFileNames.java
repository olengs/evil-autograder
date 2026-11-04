// Defines the filename conventions shared by submission loading and identification.
// Recognizes ZIP suffixes and removes them without changing username spelling.

package submission;

import java.nio.file.Path;
import java.util.Locale;

public final class SubmissionFileNames {
    private static final String ZIP_EXTENSION = ".zip";

    private SubmissionFileNames() {}

    public static boolean hasZipExtension(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION);
    }

    /** Callers supply a ZIP filename; academic-year prefixes remain in the returned name. */
    public static String baseNameWithoutZipExtension(Path zipFile) {
        String name = zipFile.getFileName().toString();
        return name.substring(0, name.length() - ZIP_EXTENSION.length());
    }
}

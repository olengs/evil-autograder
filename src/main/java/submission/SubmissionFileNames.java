// Defines the filename conventions shared by submission loading and identification.
// Recognizes ZIP suffixes and removes them without changing username spelling.

package submission;

import java.nio.file.Path;
import java.util.Locale;

public final class SubmissionFileNames {
    private static final String zipExtension = ".zip";

    private SubmissionFileNames() {}

    //@1ejun6 > check zip extension > ignore letter case
    public static boolean hasZipExtension(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(zipExtension);
    }

    /** Callers supply a ZIP filename; academic-year prefixes remain in the returned name. */
    private static String baseNameWithoutZipExtension(Path zipFile) {
        String name = zipFile.getFileName().toString();
        return name.substring(0, name.length() - zipExtension.length());
    }

    //@1ejun6 > remove zip extension & academic year prefix > username
    public static String username(Path zipFile) {
        return baseNameWithoutZipExtension(zipFile).replaceFirst("^\\d{4}-\\d{4}-", "");
    }
}

//defines zip filename conventions used to recognize archives & derive usernames
//shares same naming rules between submission identification & creating a student list when none is provided

package submission;

import java.nio.file.Path;
import java.util.Locale;

public final class SubmissionFileNames {
    private static final String zipExtension = ".zip";

    private SubmissionFileNames() {}

    public static boolean hasZipExtension(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(zipExtension);
    }

    //callers supply a zip filename > academic-year prefixes remain in returned name
    private static String baseNameWithoutZipExtension(Path zipFile) {
        String name = zipFile.getFileName().toString();
        return name.substring(0, name.length() - zipExtension.length());
    }

    public static String username(Path zipFile) {
        return baseNameWithoutZipExtension(zipFile).replaceFirst("^\\d{4}-\\d{4}-", "");
    }
}

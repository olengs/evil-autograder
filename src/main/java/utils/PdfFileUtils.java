package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfFileUtils { //checks for a pdf that is about to be uploaded to openai
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private PdfFileUtils() {
    }

    // return the normalised path when it is a readable, non-empty pdf > otherwise fail with a clear message
    public static Path requirePdf(Path pdfFile) throws IOException {
        if (pdfFile == null) {
            throw new IOException("exam pdf path is required");
        }
        Path normalised = pdfFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalised)) {
            throw new IOException("exam pdf not found > " + normalised);
        }
        if (!Files.isReadable(normalised)) {
            throw new IOException("exam pdf is not readable > " + normalised);
        }
        if (Files.size(normalised) == 0) {
            throw new IOException("exam pdf is empty > " + normalised);
        }
        // a renamed non-pdf would only fail later inside the provider with a vague error
        try (var in = Files.newInputStream(normalised)) {
            byte[] header = in.readNBytes(PDF_MAGIC.length);
            if (!java.util.Arrays.equals(header, PDF_MAGIC)) {
                throw new IOException("file is not a pdf > " + normalised);
            }
        }
        return normalised;
    }
}

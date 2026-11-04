import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import utils.PdfFileUtils;

class PdfFileUtilsTest {
    @TempDir
    Path dir;

    @Test
    void rejectsMissingEmptyAndNonPdfFiles() throws IOException {
        Path empty = Files.createFile(dir.resolve("empty.pdf"));
        Path fake = Files.writeString(dir.resolve("fake.pdf"), "hello");

        assertThrows(IOException.class, () -> PdfFileUtils.requirePdf(dir.resolve("missing.pdf")));
        assertThrows(IOException.class, () -> PdfFileUtils.requirePdf(empty));
        assertThrows(IOException.class, () -> PdfFileUtils.requirePdf(fake));
    }

    @Test
    void acceptsRealPdfHeader() throws IOException {
        Path pdf = Files.writeString(dir.resolve("exam.pdf"), "%PDF-1.4\n%%EOF\n");

        assertEquals(pdf.toAbsolutePath().normalize(), PdfFileUtils.requirePdf(pdf));
        assertEquals("exam.pdf", PdfFileUtils.displayName(pdf));
    }
}

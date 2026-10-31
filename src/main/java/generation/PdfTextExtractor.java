package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import exceptions.ProcessExecutionException;
import model.ProcessResult;
import utils.ProcessExecutor;

public class PdfTextExtractor { //reads a pdf into plain text > pdfbox first & pdftotext as a fallback
    // time limit for the external pdftotext fallback so a stuck tool cannot hang generation
    private static final Duration PDFTOTEXT_TIMEOUT = Duration.ofSeconds(30); // TODO: in config?

    public ExamDocument extract(Path pdfFile) throws IOException {
        Path normalised = pdfFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalised)) {
            throw new IOException("exam pdf not found > " + normalised);
        }
        if (!Files.isReadable(normalised)) {
            throw new IOException("exam pdf is not readable > " + normalised);
        }

        // try the bundled library first > it needs no external tool & works cross-platform
        String text = extractWithPdfBox(normalised);

        // fall back to the pdftotext cli only when the library returned nothing usable
        if (text == null || text.isBlank()) {
            text = extractWithPdftotext(normalised);
        }

        // ExamDocument rejects blank text > a scanned or image-only pdf fails here with a clear message
        return new ExamDocument(normalised, text);
    }

    private String extractWithPdfBox(Path pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // keep page order so question headings stay in sequence for later slicing
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException | RuntimeException error) {
            // do not fail yet > let the pdftotext fallback try before giving up
            return null;
        }
    }

    private String extractWithPdftotext(Path pdfFile) {
        // "-layout" keeps columns readable > "-" writes extracted text to stdout
        ProcessBuilder builder = new ProcessBuilder("pdftotext", "-layout", pdfFile.toString(), "-");
        try {
            ProcessResult result = ProcessExecutor.run(builder, PDFTOTEXT_TIMEOUT,
                    "pdf-001", "pdftotext could not be started");
            if (result.isTimedOut() || !result.isSuccessful()) {
                return null;
            }
            return result.getStandardOutput();
        } catch (ProcessExecutionException error) {
            // pdftotext missing or failed > nothing left to try > caller sees the blank-text error
            return null;
        }
    }
}

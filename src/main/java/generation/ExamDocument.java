package generation;

import java.nio.file.Path;

public final class ExamDocument { //the exam pdf & its extracted plain text > question slicing happens later in collection
    private final Path pdfFile;
    private final String text; // full extracted text of the exam

    public ExamDocument(Path pdfFile, String text) {
        if (pdfFile == null) {
            throw new IllegalArgumentException("pdfFile is required");
        }
        if (text == null || text.isBlank()) {
            // empty text means a scanned or unreadable pdf > fail loudly rather than feed the llm nothing
            throw new IllegalArgumentException("exam pdf produced no extractable text > " + pdfFile);
        }
        this.pdfFile = pdfFile;
        this.text = text;
    }

    public Path getPdfFile() {
        return pdfFile;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "ExamDocument{pdfFile=" + pdfFile + ", textLength=" + text.length() + '}';
    }
}

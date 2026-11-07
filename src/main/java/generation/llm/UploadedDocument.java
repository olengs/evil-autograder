package generation.llm;

// an exam pdf already uploaded to a provider > reference is what a request points at (openai file id)
// name is what delete needs back (openai file id)
public record UploadedDocument(String reference, String name) {
}

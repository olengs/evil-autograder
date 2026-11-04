package generation.llm;

// an exam pdf already uploaded to a provider > reference is what a request points at (gemini file uri / openai file id)
// name is what delete needs back (gemini file name / openai file id)
public record UploadedDocument(String reference, String name) {
}

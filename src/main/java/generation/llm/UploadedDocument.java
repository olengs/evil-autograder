package generation.llm;

// a pdf already uploaded to a provider > reference is the gemini file uri or the openai file id
// name is what delete needs back > gemini file name or the openai file id
public record UploadedDocument(String reference, String name, String mimeType, String displayName) {
}

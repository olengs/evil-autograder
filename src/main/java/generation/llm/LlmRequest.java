package generation.llm;

//one combined generation request > document is an optional uploaded pdf file id
public record LlmRequest(String model, String prompt, String document) {
    public LlmRequest {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
    }

    public boolean hasDocument() {
        return document != null;
    }

    @Override
    public String toString() {
        return "LlmRequest{model=" + model + ", promptLength=" + prompt.length() + ", hasDocument=" + hasDocument() + '}';
    }
}

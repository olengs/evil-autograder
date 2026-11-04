package generation.llm;

import java.util.Optional;

public final class LlmRequest { //one call to a model > model name & prompt text & optional uploaded pdf
    private final String model;
    private final String prompt; // the full assembled prompt text
    private final Optional<UploadedDocument> document; // present only when the exam pdf was uploaded

    public LlmRequest(String model, String prompt, UploadedDocument document) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        this.model = model;
        this.prompt = prompt;
        this.document = Optional.ofNullable(document);
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean hasDocument() {
        return document.isPresent();
    }

    public UploadedDocument getDocument() {
        return document.orElse(null);
    }

    @Override
    public String toString() {
        return "LlmRequest{model=" + model + ", promptLength=" + prompt.length() + ", hasDocument=" + hasDocument() + '}';
    }
}

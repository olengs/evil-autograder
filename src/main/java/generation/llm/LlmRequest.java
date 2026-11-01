package generation.llm;

import java.util.Optional;

public final class LlmRequest { //one call to a model > model name & prompt text & optional pdf bytes for native-pdf mode
    private final String model;
    private final String prompt; // the full assembled prompt text
    private final Optional<byte[]> pdfBytes; // present only when sending the pdf natively (gemini)

    public LlmRequest(String model, String prompt, byte[] pdfBytes) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        this.model = model;
        this.prompt = prompt;
        // copy the array so the request stays immutable > empty when not sending a pdf
        this.pdfBytes = (pdfBytes == null) ? Optional.empty() : Optional.of(pdfBytes.clone());
    }

    // text-only convenience constructor
    public LlmRequest(String model, String prompt) {
        this(model, prompt, null);
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean hasPdf() {
        return pdfBytes.isPresent();
    }

    // defensive copy out > callers cannot mutate the stored bytes
    public byte[] getPdfBytes() {
        return pdfBytes.map(byte[]::clone).orElse(null);
    }

    @Override
    public String toString() {
        return "LlmRequest{model=" + model + ", promptLength=" + prompt.length() + ", hasPdf=" + hasPdf() + '}';
    }
}

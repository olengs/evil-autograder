package generation;

// one file from a question folder as shown to the llm > content is null for a binary file e.g. DataException.class
public record QuestionFile(String name, String content) {
    public QuestionFile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("question file name is required");
        }
    }

    public boolean isBinary() {
        return content == null;
    }
}

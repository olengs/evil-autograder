package generation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import config.AssessmentLoader;
import model.AssessmentDefinition;
import model.QuestionDefinition;

public class QuestionSpecCollector { //turns answer-template folders into one QuestionSpec per question
    public List<QuestionSpec> collect(Path resourcesDirectory) throws IOException {
        // reuse the grader's own discovery so the generated testers match exactly what will be graded
        AssessmentDefinition assessment = AssessmentLoader.load(resourcesDirectory);

        List<QuestionSpec> specs = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            // the template source is the authoritative method signature for the llm
            String templateSource = Files.readString(resolveTemplateFile(question));

            // carry the question folder & its support files so validation can compile like the real grader
            // & every file in the folder so the llm sees the data files & sibling templates too
            specs.add(new QuestionSpec(question.getId(), templateSource,
                    question.getResourceDirectory(), question.getAllowedResourceFiles(),
                    readFolderFiles(question.getResourceDirectory())));
        }
        return specs;
    }

    // the template file is the only answer file for this question > folder/id.java e.g. Q1/Q1a.java
    private Path resolveTemplateFile(QuestionDefinition question) throws IOException {
        List<String> required = question.getRequiredFiles();
        if (required.isEmpty()) {
            throw new IOException("no answer template file for question " + question.getId());
        }
        return question.getResourceDirectory().resolve(required.get(0));
    }

    // every non-hidden file in the question folder sorted by name > binary files keep their name only
    private List<QuestionFile> readFolderFiles(Path folder) throws IOException {
        List<Path> paths;
        try (var entries = Files.list(folder)) {
            paths = entries.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith(".")).sorted().toList();
        }
        List<QuestionFile> files = new ArrayList<>();
        for (Path path : paths) {
            files.add(new QuestionFile(path.getFileName().toString(), readText(path)));
        }
        return files;
    }

    // strict utf-8 decode > null when the file is not text e.g. a compiled .class
    private String readText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return text.indexOf('\0') >= 0 ? null : text;
        } catch (CharacterCodingException error) {
            return null;
        }
    }
}

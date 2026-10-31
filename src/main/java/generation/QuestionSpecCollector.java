package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import config.AssessmentLoader;
import model.AssessmentDefinition;
import model.QuestionDefinition;

public class QuestionSpecCollector { //turns answer-template folders + exam text into one QuestionSpec per question
    // question id looks like Q1a or Q3 > capture the number & the optional sub-part letter
    private static final Pattern QUESTION_ID = Pattern.compile("(?i)^Q(\\d+)([a-z]?)$");

    public List<QuestionSpec> collect(Path resourcesDirectory, ExamDocument exam) throws IOException {
        // reuse the grader's own discovery so the generated testers match exactly what will be graded
        AssessmentDefinition assessment = AssessmentLoader.load(resourcesDirectory);

        List<QuestionSpec> specs = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            String id = question.getId();

            // read the template source > this is the authoritative method signature for the llm
            Path templateFile = resolveTemplateFile(question);
            String templateSource = Files.readString(templateFile);

            String excerpt = sliceExcerpt(exam.getText(), id);
            specs.add(new QuestionSpec(id, question.getExpectedFolder(), templateFile, templateSource, excerpt));
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

    // slice the exam text down to the part describing one question > blank when it cannot be located
    String sliceExcerpt(String examText, String questionId) {
        Matcher idMatcher = QUESTION_ID.matcher(questionId);
        if (!idMatcher.matches()) {
            return ""; // unexpected id shape > no reliable excerpt
        }
        int number = Integer.parseInt(idMatcher.group(1));
        String letter = idMatcher.group(2).toLowerCase(Locale.ROOT); // "" when the question has no sub-parts

        String block = sliceQuestionBlock(examText, number);
        if (block.isBlank()) {
            return "";
        }
        if (letter.isEmpty()) {
            return block.strip(); // whole-question like Q3 > return the entire block
        }
        return sliceSubPart(block, letter.charAt(0)).strip();
    }

    // text from "Question N" up to "Question N+1" (or end of document)
    private String sliceQuestionBlock(String examText, int number) {
        Pattern start = Pattern.compile("(?im)^\\s*Question\\s+" + number + "\\b");
        Matcher startMatcher = start.matcher(examText);
        if (!startMatcher.find()) {
            return "";
        }
        int from = startMatcher.start();

        Pattern next = Pattern.compile("(?im)^\\s*Question\\s+" + (number + 1) + "\\b");
        Matcher nextMatcher = next.matcher(examText);
        int to = nextMatcher.find(from + 1) ? nextMatcher.start() : examText.length();
        return examText.substring(from, to);
    }

    // within one question block > text from "(a)" up to the next "(b)" marker (or end of block)
    private String sliceSubPart(String block, char letter) {
        Pattern start = Pattern.compile("(?m)^\\s*\\(" + letter + "\\)");
        Matcher startMatcher = start.matcher(block);
        if (!startMatcher.find()) {
            // sub-part marker missing > fall back to the whole block rather than an empty excerpt
            return block;
        }
        int from = startMatcher.start();

        // next sub-part letter closes this one > e.g. (b) ends (a)
        char nextLetter = (char) (letter + 1);
        Pattern next = Pattern.compile("(?m)^\\s*\\(" + nextLetter + "\\)");
        Matcher nextMatcher = next.matcher(block);
        int to = nextMatcher.find(from + 1) ? nextMatcher.start() : block.length();
        return block.substring(from, to);
    }
}

package generation.schema;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// the output format sent to the model > {"tests": [{"id": ..., "testCode": ...}]} > one entry per question
// a wrapper object because strict json schemas need an object at the root > not a bare array
@JsonClassDescription("Every tester requested in the prompt.")
public class GeneratedTesters {
    @JsonPropertyDescription("One tester per question id listed in the prompt > every listed id exactly once.")
    public List<Tester> tests;

    // no @JsonClassDescription here > a class description plus a property description on the same object
    // becomes allOf, which openai strict mode rejects > describe the fields only
    public static class Tester {
        @JsonPropertyDescription("The question id this tester is for, exactly as listed in the prompt, e.g. \"Q1a\". "
                + "The tester is written to <id>Tester.java.")
        public String id;

        @JsonPropertyDescription("Positive integer mark allocation from the exam; exactly this many one-mark cases. Zero when unknown.")
        public int marks;

        @JsonPropertyDescription("Empty for a complete tester. Otherwise explain missing or conflicting requirements and leave testCode empty.")
        public String issue;

        @JsonPropertyDescription("The complete Java source of <id>Tester.java, following the tester template in the prompt. "
                + "Plain source only > no markdown fences. Empty when issue is nonempty.")
        public String testCode;
    }
}

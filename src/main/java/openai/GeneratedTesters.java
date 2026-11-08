package openai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

//output format sent to model > {"tests": [{"id": ..., "testCode": ...}]} > one entry per question
//a wrapper object because strict json schemas need an object at root > not a bare array
@JsonClassDescription("Every tester requested in prompt.")
public class GeneratedTesters {
    @JsonPropertyDescription("One tester per question id listed in prompt > every listed id exactly once.")
    public List<Tester> tests;

    //no @JsonClassDescription here > a class description plus a property description on same object
    //becomes allOf, which openai strict mode rejects > describe fields only
    public static class Tester {
        @JsonPropertyDescription("The question id this tester is for, exactly as listed in prompt, e.g. \"Q1a\". "
                + "The tester is written to <id>Tester.java.")
        public String id;

        @JsonPropertyDescription("Positive integer mark allocation from exam; exactly this many one-mark cases. Zero when unknown.")
        public int marks;

        @JsonPropertyDescription("Empty for a complete tester. Otherwise explain missing or conflicting requirements and leave testCode empty.")
        public String issue;

        @JsonPropertyDescription("The complete Java source of <id>Tester.java, following tester template in prompt. "
                + "Plain source only > no markdown fences. Empty when issue is nonempty.")
        public String testCode;
    }
}

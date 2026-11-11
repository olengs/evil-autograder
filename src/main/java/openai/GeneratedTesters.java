package openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

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

        @JsonPropertyDescription("Positive integer number of test cases, a multiple of marks. For 2.5 marks use 5 or 10 cases. Zero when unknown.")
        public int numTestCases;

        @JsonPropertyDescription("Positive whole or half mark allocation from the exam PDF, e.g. 2 or 2.5; zero when unknown.")
        public float marks;

        @JsonPropertyDescription("Empty for a complete tester. Otherwise explain missing or conflicting requirements and leave testCode empty.")
        public String issue;

        @JsonPropertyDescription("The complete Java source of <id>Tester.java, following tester template in prompt. "
                + "Plain source only > no markdown fences. Empty when issue is nonempty.")
        public String testCode;

        @JsonPropertyDescription("Text resource files required by this tester, each with fileName and content. Use [] when none are needed. Filenames must match paths used by testCode.")
        public List<ResourceFile> resources;
    }
    public static class ResourceFile {
        @JsonPropertyDescription("Simple filename beside the tester, e.g. personstester.txt. No directories or Java source/class files.")
        public String fileName;

        @JsonPropertyDescription("Complete UTF-8 text contents in the input format expected by the question.")
        public String content;
    }
}

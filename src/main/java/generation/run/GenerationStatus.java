package generation.run;

public enum GenerationStatus { //outcome kind for one question's tester generation
    WRITTEN, // generated & validated & written to the output folder
    VALIDATION_FAILED, // invalid source is saved under review/, outside grader discovery
    GENERATION_FAILED // llm call or code extraction failed > nothing produced
}

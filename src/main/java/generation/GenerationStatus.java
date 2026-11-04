package generation;

public enum GenerationStatus { //outcome kind for one question's tester generation
    WRITTEN, // generated & validated & written to the output folder
    VALIDATION_FAILED, // generated but failed compile or run or parse > still written for manual review when possible
    GENERATION_FAILED // llm call or code extraction failed > nothing produced
}

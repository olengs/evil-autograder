package model;

public enum GradeStatus { //lists possible statuses of a question grade
    COMPLETED, //compiled & ran & test cases were parsed
    COMPILE_ERROR, //student code or tester failed to compile
    RUNTIME_ERROR, //tester exited abnormally while running
    TIMEOUT, //tester exceeded time limit
    SKIPPED, //attempt missing files or invalid
    PENDING //waiting for processing
}

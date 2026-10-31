package model;

public enum GradeStatus { //lists possible statuses of a question grade
    completed, //compiled & ran & test cases were parsed
    compile_error, //student code or tester failed to compile
    runtime_error, //tester exited abnormally while running
    timeout, //tester exceeded time limit
    skipped, //attempt missing files or invalid
    pending //waiting for processing
}

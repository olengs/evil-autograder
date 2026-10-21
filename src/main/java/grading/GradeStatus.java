package grading;

public enum GradeStatus { //defines completed, failed, timed-out & skipped grading outcomes
    completed, //compilation & execution & output parsing finished > individual cases may still fail
    compileError,
    runtimeError,
    timeout, //compilation or execution exceeded its time limit
    skipped //validation & a missing attempt or a missing tester prevented grading
}

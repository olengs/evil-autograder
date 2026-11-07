//lists final outcomes a question grade can have
//distinguishes completed grading & compilation or runtime failures & timeouts & skipped questions

package grading;

public enum GradeStatus {
    completed, //compilation & execution & output parsing finished > individual cases may still fail
    compileError,
    runtimeError,
    timeout, //compilation or execution exceeded its time limit
    skipped //validation & a missing attempt or a missing tester prevented grading
}

package autograder.grading;

import autograder.model.GradeStatus;
import autograder.model.ProcessResult;
import autograder.model.QuestionGrade;
import autograder.model.TestCaseResult;

import java.util.regex.Pattern;
import java.util.regex.Matcher; 
import java.util.Arrays;
import java.util.List; 


public class QuestionGrader {
    // private static final Pattern delimiter = Pattern.compile("[-+]");
    
    
    public QuestionGrade gradeQuestion(String questionId,ProcessResult questionResults){
        // question did not went through process at all
        if(questionResults == null){
            return new QuestionGrade(questionId, GradeStatus.SKIPPED, 0, null, processResult, "Question is skipped");
        }

        // any error in compile or execution && is not timeout
        if(!questionResults.isSuccessful() && !questionResults.isTimedOut()){
            // when javac cannot run
            if(questionResults.getErrorOutput().contains("prc-001")){
                GradeStatus status = GradeStatus.COMPILE_ERROR;
                String message = "Compilation failed.";
            }
            // the rest of the execution error & java 
            else if(questionResults.getErrorOutput().contains("prc")){
                GradeStatus status = GradeStatus.RUNTIME_ERROR;
                String message = "Execution of tester file failed.";

            }
            return new QuestionGrade(questionId, status, 0, null, questionResults, message);
        }

        
        String questionOutput = questionResults.getStandardOutput();
        String questionError = questionResults.getErrorOutput();
        List<TestCaseResult> testCaseResults = new List<TestCaseResult>();
        Integer totalMarks = 0;

        String[] testOutputs = questionOutput.split("Test ");
        for (String testOutput : testOutputs) {
            // TODO: check if question needs a pass attribute
            String testId = testOutput.substring(0, testOutput.indexOf(":"));
            Integer passedIndex = testOutput.indexOf("Passed");
            Integer earnedMarks = passedIndex>=0? 1:0;
            boolean passed = passedIndex>=0;
            TestCaseResult testCase = new TestCaseResult(testId,passed, earnedMarks);
            
            testCaseResults.add(testCase);
            totalMarks += earnedMarks;
        }

        if ( questionResult.isTimedOut()){
            return new QuestionGrade(questionId,GradeStatus.TIMEOUT,totalMarks,testCaseResults,questionResults,"Question got timed out");
        }else{
            // GradeStatus status = new GradeStatus(GradeStatus.COMPLETED);
            return new QuestionGrade(questionId,GradeStatus.COMPLETED,totalMarks,testCaseResults,questionResults,"Grading Successful");

        }
        // return null;
    }
}

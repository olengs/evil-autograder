

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertNull;
// import static org.junit.jupiter.api.Assertions.assertSame;

// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;

// import autograder.grading.QuestionGrader;
// import autograder.model.QuestionGrade;
// import model.QuestionAttempt;
// import autograder.model.GradeStatus;

// // groundtruth
// // chee.teo.2022: Q1A:3.0   Q1B:3.0    Q2A:5.0    Q2B:1.0   Q3:0.0  Total: 12.0
// // david.2024:    Q1A:3.0   Q1B:3.0    Q2A:4.0    Q2B:0.0   Q3:0.0  Total: 10.0
// // fen.lai.2022:  Q1A:1.0   Q1B:0.0    Q2A:5.0    Q2B:4.0   Q3:0.0  Total: 10.0
// // jing.lim.2021: Q1A:0.0   Q1B:0.0    Q2A:0.0    Q2B:0.0   Q3:0.0  Total: 0.0
// // ping.lee.2023: Q1A:3.0   Q1B:3.0    Q2A:5.0    Q2B:5.0   Q3:4.0  Total: 20.0
// // xing.yan.2023: Q1A:0.0   Q1B:1.0    Q2A:5.0    Q2B:5.0   Q3:4.0  Total: 1.0

// /** Tests for {@link QuestionGrader}. */
// class QuestionGraderTest {

//     // ===== chee.teo.2022 =====

//     @Test
//     @DisplayName("chee.teo.2022 Q1A")
//     void chee_Q1A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(3.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("chee.teo.2022 Q1B")
//     void chee_Q1B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(3.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("chee.teo.2022 Q2A")
//     void chee_Q2A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(5.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("chee.teo.2022 Q2B")
//     void chee_Q2B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(1.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("chee.teo.2022 Q3")
//     void chee_Q3() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     // ===== david.2024 =====

//     @Test
//     @DisplayName("david.2024 Q1A")
//     void david_Q1A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(3.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("david.2024 Q1B")
//     void david_Q1B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(3.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("david.2024 Q2A")
//     void david_Q2A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(4.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("david.2024 Q2B")
//     void david_Q2B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("david.2024 Q3")
//     void david_Q3() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     // ===== fen.lai.2022 =====

//     @Test
//     @DisplayName("fen.lai.2022 Q1A")
//     void fen_Q1A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(1.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("fen.lai.2022 Q1B")
//     void fen_Q1B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("fen.lai.2022 Q2A")
//     void fen_Q2A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(5.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("fen.lai.2022 Q2B")
//     void fen_Q2B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(4.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("fen.lai.2022 Q3")
//     void fen_Q3() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     // ===== jing.lim.2021 =====

//     @Test
//     @DisplayName("jing.lim.2021 Q1A")
//     void jing_Q1A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("jing.lim.2021 Q1B")
//     void jing_Q1B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("jing.lim.2021 Q2A")
//     void jing_Q2A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("jing.lim.2021 Q2B")
//     void jing_Q2B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("jing.lim.2021 Q3")
//     void jing_Q3() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     // ===== ping.lee.2023 =====

//     @Test
//     @DisplayName("ping.lee.2023 Q1A")
//     void ping_Q1A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(3.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("ping.lee.2023 Q1B")
//     void ping_Q1B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(3.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("ping.lee.2023 Q2A")
//     void ping_Q2A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(5.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("ping.lee.2023 Q2B")
//     void ping_Q2B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(5.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("ping.lee.2023 Q3")
//     void ping_Q3() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(4.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     // ===== xing.yan.2023 =====

//     @Test
//     @DisplayName("xing.yan.2023 Q1A")
//     void xing_Q1A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(0.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("xing.yan.2023 Q1B")
//     void xing_Q1B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(1.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("xing.yan.2023 Q2A")
//     void xing_Q2A() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(5.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("xing.yan.2023 Q2B")
//     void xing_Q2B() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(5.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }

//     @Test
//     @DisplayName("xing.yan.2023 Q3")
//     void xing_Q3() {
//         // pair1 to get question attemp
//         QuestionAttempt attempt;
//         QuestionGrade grade = new QuestionGrader.grade(attempt);
//         assertEquals(4.0, grade.getEarnedMarks());
//         assertEquals(GradeStatus.PENDING, grade.getStatus()); // need to find out by running, should only have COMPLETES, TIMEOUT and SKIPPED
//     }
// }

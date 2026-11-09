package assessment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesterMetadataTest {
    @TempDir Path root;

    @Test
    void loadingWritesExplicitCountsBeforeAnyGrading() throws Exception {
        Path folder = Files.createDirectories(root.resolve("Q1"));
        Files.writeString(root.resolve("marks.txt"), "Q1a:3\nQ1b:3\n");
        Files.writeString(folder.resolve("Q1aTester.java"), """
                public class Q1aTester {
                    public static void grade() {
                        System.out.println("--- " + "banner");
                        // System.out.println("Test 99: comment is not a case");
                        System.out.println("Passed");
                        System.out.println ( "Passed" );
                        System.out.println("Failed");
                    }
                }
                """);
        Files.writeString(folder.resolve("Q1bTester.java"), """
                public class Q1bTester {
                    public static void grade() {
                        for (int i = 0; i < 10; i++) System.out.printf("Test %d: case%n", i);
                    }
                }
                """);
        var testers = TesterLoader.load(root);
        assertEquals(2, testers.get("q1a").getNumTestCases());
        assertEquals(0, testers.get("q1b").getNumTestCases());
        assertEquals("Q1a:2\n", Files.readString(root.resolve("numTestCases.txt")));
        TesterLoader.load(root);
        assertEquals("Q1a:2\n", Files.readString(root.resolve("numTestCases.txt")));
    }

    @Test
    void preservesExistingCountsAndAddsMissingEntries() throws Exception {
        Path file = root.resolve("numTestCases.txt");
        Files.writeString(file, "Q1a:6");
        assertEquals(6, TesterMetadata.recordCaseCount(root, "q1A", 10));
        assertEquals("Q1a:6", Files.readString(file));
        TesterMetadata.recordCaseCount(root, "Q1b", 8);
        assertEquals("Q1a:6\nQ1b:8\n", Files.readString(file));
    }

    @Test
    void concurrentWorkersDoNotDuplicateOrLoseEntries() throws Exception {
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 24; i++) {
                String id = "Q" + (i % 4);
                tasks.add(workers.submit(() -> TesterMetadata.recordCaseCount(root, id, 10)));
            }
            for (var task : tasks) assertEquals(10, task.get());
        }
        assertEquals(4, TesterMetadata.readCaseCounts(root).size());
        assertEquals(4, Files.readAllLines(root.resolve("numTestCases.txt")).size());
    }
}

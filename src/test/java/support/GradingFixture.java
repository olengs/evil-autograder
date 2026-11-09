package support;

import config.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small, deterministic assessment; never reads or writes the instructor's project. */
public final class GradingFixture {
    private GradingFixture() {}

    public static Config create(Path root) throws IOException {
        Path resources = Files.createDirectories(root.resolve("resources/Q1"));
        Files.writeString(resources.resolve("Q1.java"), "public class Q1 {}\n");
        Path testers = Files.createDirectories(root.resolve("testers"));
        Files.writeString(testers.resolve("Q1Tester.java"), """
                public class Q1Tester {
                    public static void main(String[] args) {
                        System.out.println("Test 1: answer");
                        System.out.println(Q1.answer() == 42 ? "Passed" : "Failed");
                    }
                }
                """);
        Path submissions = Files.createDirectories(root.resolve("submissions"));
        zip(submissions.resolve("alice.test.2026.zip"), "return 42;");
        zip(submissions.resolve("bob.test.2026.zip"), "return 0;");
        zip(submissions.resolve("carol.test.2026.zip"), "return ;");
        Properties properties = new Properties();
        properties.setProperty("input.directory", root.toString());
        properties.setProperty("output.directory", root.resolve("output").toString());
        properties.setProperty("submissions.folder", "submissions");
        properties.setProperty("resources.folder", "resources");
        properties.setProperty("testers.folder", "testers");
        properties.setProperty("scoresheet.file", "grades.csv");
        properties.setProperty("grading.workers", "2");
        properties.setProperty("compilation.timeout.seconds", "30");
        properties.setProperty("execution.timeout.seconds", "5");
        Path file = root.resolve("config.properties");
        try (var writer = Files.newBufferedWriter(file)) {
            properties.store(writer, "test fixture");
        }
        return new Config(file);
    }

    private static void zip(Path file, String body) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("Q1/Q1.java"));
            zip.write(("public class Q1 { public static int answer() { " + body + " } }").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}

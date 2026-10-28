package app;

import grading.AssessmentGrader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            String projectPath;
            if (args.length == 0) {
                System.out.print("project folder path > ");
                projectPath = new Scanner(System.in).nextLine().strip();
            } else {
                projectPath = args[0];
            }
            AssessmentGrader.run(Path.of(projectPath));
        } catch (IOException error) {
            System.err.println("error > " + error.getMessage());
        }
    }
}

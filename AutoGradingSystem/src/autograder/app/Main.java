package autograder.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import autograder.submission.SubmissionLoader;

public class Main {
    
    public static void main(String[] args) 
    {
        SubmissionLoader loader = new SubmissionLoader();

        // Testing purpose: can test with your path with zip
        Path submissionFolder = Path.of("C:\\Users\\Jerem\\Documents\\SMU Module\\Y2T1\\CS102 - JAVA\\ICE6-exception1-resource-2026");

        Path workspaceFolder = Path.of("C:\\Users\\Jerem\\Documents\\SMU Module\\Y2T1\\CS102 - JAVA\\ICE6-exception1-resource-2026");

        try {
            List<Path> extractedFolders = loader.loadSubmissions(submissionFolder,workspaceFolder);

            System.out.println("Loaded submissions: "+ extractedFolders.size());

            for (Path folder : extractedFolders) 
                {System.out.println( "Extracted to: " + folder);}



            // run normalised folder to fix folder issues then create student object and create studentSubmission object

            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

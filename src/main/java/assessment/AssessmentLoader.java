package assessment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import utils.files.FileUtils;

/// discovers question templates & their support files
public final class AssessmentLoader { 
    private AssessmentLoader() {}

    public static AssessmentDefinition load(Path resources) throws IOException {
        Path root = resources.toAbsolutePath().normalize();
        List<QuestionDefinition> questions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<Path> folders;

        try (var entries = Files.list(root)) {
            folders = entries.filter(Files::isDirectory).sorted().toList();
        }
        for (Path folder : folders) {
            String folderName = folder.getFileName().toString();

            if (folderName.startsWith(".")) continue;

            //q1/q1a.java defines a question > other files in q1 provide support
            Pattern answerName = Pattern.compile(Pattern.quote(folderName) + "[a-zA-Z]*\\.java");
            List<String> files;
            try (var entries = FileUtils.streamRegularFiles(folder)) {
                files = entries.map(path -> path.getFileName().toString()).filter(name -> !name.startsWith(".")).sorted().toList();
            }

            List<String> answers = files.stream().filter(name -> answerName.matcher(name).matches()).toList();
            if (answers.isEmpty()) continue;

            Set<String> answerClasses = new HashSet<>();
            for (String answer : answers) answerClasses.add(answer.substring(0, answer.length() - 5));

            //exclude reference answers & compiled classes from supplied support
            List<String> support = files.stream().filter(name -> !answers.contains(name)).filter(name -> !isAnswerClass(name, answerClasses)).toList();
            for (String answer : answers) {
                String id = answer.substring(0, answer.length() - 5);
                if (!ids.add(id.toLowerCase(Locale.ROOT))) throw new IOException("duplicate question id > " + id);
                questions.add(new QuestionDefinition(id, folderName, root, List.of(answer), support));
            }
        }
        if (questions.isEmpty()) throw new IOException("no question templates found in " + root + " > expected folder/question.java such as q1/q1a.java");

        return new AssessmentDefinition(questions);
    }

    private static boolean isAnswerClass(String file, Set<String> answers) {
        if (!file.endsWith(".class")) return false;
        String name = file.substring(0, file.length() - 6);
        int nested = name.indexOf('$');
        return answers.contains(nested < 0 ? name : name.substring(0, nested));
    }
}

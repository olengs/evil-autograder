package generation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TesterCodeExtractor { //pulls clean java source out of an llm reply > strips markdown fences & prose
    // a fenced block like ```java ... ``` or ``` ... ``` > capture the inner source
    private static final Pattern FENCED_BLOCK =
            Pattern.compile("```[a-zA-Z0-9]*\\s*\\n(.*?)\\n?```", Pattern.DOTALL);

    // extract the java source the llm produced > throw when nothing usable is present
    public String extract(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("empty llm response > no tester code to extract");
        }

        String code = firstFencedBlock(response);
        if (code == null) {
            // no fences > assume the whole reply is source & trim stray prose around it
            code = response;
        }

        code = trimToSource(code).strip();
        if (code.isBlank()) {
            throw new IllegalArgumentException("llm response contained no java source");
        }
        return code;
    }

    // return the contents of the first fenced block > null when there are no fences
    private String firstFencedBlock(String response) {
        Matcher matcher = FENCED_BLOCK.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // a single opening fence with no closing one > take everything after it
        int fence = response.indexOf("```");
        if (fence >= 0) {
            int lineEnd = response.indexOf('\n', fence);
            if (lineEnd >= 0) {
                return response.substring(lineEnd + 1).replace("```", "");
            }
        }
        return null;
    }

    // drop any leading prose before the real source starts
    // source starts at the first import, a package line, or a top-level type declaration
    private String trimToSource(String code) {
        Matcher start = Pattern.compile(
                "(?m)^\\s*(package\\s|import\\s|public\\s+(final\\s+)?(class|interface|enum|abstract)\\b|(final\\s+)?(class|interface|enum)\\b)")
                .matcher(code);
        if (start.find()) {
            return code.substring(start.start());
        }
        return code;
    }
}

package utils.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateUtils { //reads bundled text resources & fills {{name}} placeholders > shared by the prompt & tester templates
    //{{name}} placeholder > filled in a single pass so inserted text is never re-scanned
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

    private TemplateUtils() {
    }

    //a utf-8 resource from src/main/resources > fail loudly when it is missing from the build
    public static String readResource(String resource) {
        try (InputStream in = TemplateUtils.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("resource not found on classpath > " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("could not read resource > " + resource, error);
        }
    }

    //replace every {{name}} with its value > an unknown name is a template typo so fail loudly
    public static String fill(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        return matcher.replaceAll(match -> {
            String value = values.get(match.group(1));
            if (value == null) {
                throw new IllegalStateException("unknown template placeholder {{" + match.group(1) + "}}");
            }
            return Matcher.quoteReplacement(value);
        });
    }
}

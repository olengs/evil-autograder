package utils.files;

import utils.logging.JsonLineLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class TemplateUtils { //reads bundled text resources
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
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TemplateUtils", error.toString());
            throw new IllegalStateException("could not read resource > " + resource, error);
        }
    }

}

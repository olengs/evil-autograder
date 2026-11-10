package utils.files;

import utils.logging.JsonLineLogger;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;

public final class TemplateUtils { //reads bundled text resources
    private TemplateUtils() {
    }

    //a utf-8 resource from src/main/resources > fail loudly when it is missing from the build
    public static String readResource(String resource) {
        var url = TemplateUtils.class.getResource(resource);
        try {
            if (url == null) {
                throw new IllegalStateException("resource not found on classpath > " + resource);
            }
            return IOUtils.toString(url, StandardCharsets.UTF_8);
        } catch (IOException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TemplateUtils", error.toString());
            throw new IllegalStateException("could not read resource > " + resource, error);
        }
    }

}

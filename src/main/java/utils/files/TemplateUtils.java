package utils.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import utils.logging.JsonLineLogger;

/// reads bundled text resources
public final class TemplateUtils { 
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

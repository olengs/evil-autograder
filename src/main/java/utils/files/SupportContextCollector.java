package utils.files;

import utils.logging.JsonLineLogger;
import generation.QuestionSpec;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import utils.process.JavaTools;
import utils.process.ProcessExecutor;
import utils.process.Interruptions;

/** Reads only declared support files and matching class documentation from one question folder. */
public final class SupportContextCollector {
    public record TextFile(String name, String type, String content) {}
    public record FolderContext(List<TextFile> files, List<String> issues) {}
    private static final Set<String> textTypes = Set.of("java", "txt", "csv", "json", "xml", "properties", "dat", "md");
    private static final long maxTextBytes = 256 * 1024;

    private SupportContextCollector() {}

    public static FolderContext collect(List<QuestionSpec> specs) throws InterruptedIOException {
        Interruptions.check("context collection interrupted");
        List<TextFile> files = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Path root = specs.getFirst().resourceDirectory();
        if (root == null) return new FolderContext(files, issues);
        Set<String> support = new TreeSet<>();
        Set<String> classes = new TreeSet<>();
        for (QuestionSpec spec : specs) support.addAll(spec.supportFiles());
        for (String name : support) {
            Interruptions.check("context collection interrupted");
            if (isExcluded(name)) continue;
            try {
                files.add(readSupportFile(root, name));
                String type = extension(name);
                if (type.equals("class")) classes.add(name.substring(0, name.length() - 6).replace('$', '.'));
                if (type.equals("java")) classes.add(name.substring(0, name.length() - 5));
            } catch (IOException | RuntimeException error) {
                JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "SupportContextCollector", error.toString());
                Interruptions.check("context collection interrupted");
                issues.add(name + ": " + error.getMessage());
            }
        }
        collectDocumentation(root, classes, files, issues);
        return new FolderContext(List.copyOf(files), List.copyOf(issues));
    }

    private static boolean isExcluded(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("groundtruth") || lower.contains("ground-truth") || lower.contains("ground_truth")
                || lower.endsWith("tester.java") || lower.endsWith("tester.class");
    }

    private static void collectDocumentation(Path root, Set<String> classes, List<TextFile> files, List<String> issues) throws InterruptedIOException {
        for (String className : classes) {
            Interruptions.check("context collection interrupted");
            String name = "api/" + className + ".html";
            if (!Files.exists(root.resolve(name))) continue;
            try {
                files.add(new TextFile(name + " (documentation)", "text", htmlText(readText(contained(root, name)))));
            } catch (IOException | RuntimeException error) {
                JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "SupportContextCollector", error.toString());
                Interruptions.check("context collection interrupted");
                issues.add(name + ": " + error.getMessage());
            }
        }
    }

    private static TextFile readSupportFile(Path root, String name) throws IOException {
        Path source = contained(root, name);
        String type = extension(name);
        if (type.equals("class")) return readClassApi(root, name);
        if (textTypes.contains(type)) return new TextFile(name, type, readText(source));
        return new TextFile(name, "", "Binary resource available at runtime; contents not included.");
    }

    private static TextFile readClassApi(Path root, String name) throws IOException {
        String className = name.substring(0, name.length() - 6).replace('/', '.').replace('\\', '.');
        var result = ProcessExecutor.run(new ProcessBuilder(
                JavaTools.getDisassemblerPath().toString(), "-public", "-classpath",
                root.toAbsolutePath().toString(), className), Duration.ofSeconds(30),
                "could not inspect dependency " + name, maxTextBytes);
        if (!result.isSuccessful()) throw new IOException("javap failed or timed out: " + result.getErrorOutput().strip());
        return new TextFile(name + " (public API)", "java", result.getStandardOutput().strip());
    }

    private static Path contained(Path root, String name) throws IOException {
        Path realRoot = root.toRealPath();
        Path source = realRoot.resolve(name).normalize().toRealPath();
        if (!source.startsWith(realRoot) || !Files.isRegularFile(source)) {
            throw new IOException("support file must be inside its question folder");
        }
        return source;
    }

    private static String readText(Path file) throws IOException {
        if (Files.size(file) > maxTextBytes) throw new IOException("context file exceeds 256 KiB; supply a smaller reference");
        return Files.readString(file).strip();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String htmlText(String html) throws IOException {
        var main = java.util.regex.Pattern.compile("(?is)<main\\b[^>]*>(.*?)</main>").matcher(html);
        if (main.find()) html = main.group(1);
        html = html.replaceAll("(?is)<(script|style|nav)\\b[^>]*>.*?</\\1>", "");
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override public void handleText(char[] data, int pos) { text.append(data).append(' '); }
            @Override public void handleEndTag(HTML.Tag tag, int pos) {
                if (tag.isBlock()) text.append('\n');
            }
        }, true);
        return text.toString().replace('\u00a0', ' ').replaceAll("[\\t ]+", " ").replaceAll("\\n\\s*\\n", "\n").strip();
    }
}

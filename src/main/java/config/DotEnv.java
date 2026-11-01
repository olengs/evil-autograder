package config;

import java.nio.file.Path;

import io.github.cdimascio.dotenv.Dotenv;

public final class DotEnv { //thin wrapper over dotenv-java > keeps secrets in a git-ignored .env file
    // host environment variables take precedence over .env entries > handled by the library
    private final Dotenv dotenv;

    private DotEnv(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    // load .env from the working directory > missing or malformed file is tolerated
    public static DotEnv load() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing() // no .env present > still read real environment variables
                .ignoreIfMalformed() // skip bad lines instead of throwing
                .load();
        return new DotEnv(dotenv);
    }

    // load a .env from a specific location > directory + filename as the library expects
    public static DotEnv load(Path envFile) {
        Path absolute = envFile.toAbsolutePath();
        Path directory = absolute.getParent();
        Dotenv dotenv = Dotenv.configure()
                .directory(directory == null ? "." : directory.toString())
                .filename(absolute.getFileName().toString())
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .load();
        return new DotEnv(dotenv);
    }

    // value from a real env var first > then the .env file > null when neither has it
    public String get(String key) {
        return dotenv.get(key);
    }

    public boolean has(String key) {
        String value = get(key);
        return value != null && !value.isBlank();
    }
}

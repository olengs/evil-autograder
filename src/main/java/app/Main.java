package app;

import config.Config;
import java.nio.file.Path;
import progress.GradingProgress;

public class Main { //chooses how to run the autograder
    public static void main(String[] args) {
        boolean tui = true;
        String configFile = null;
        for (String arg : args) {
            if (arg.equals("--notui")) tui = false;
            else if (!arg.startsWith("--") && configFile == null) configFile = arg;
            else {
                System.err.println("usage: app.Main [--notui] [config-file]");
                System.exit(1);
                return;
            }
        }
        if (tui) {
            TuiMain.main(configFile == null ? new String[0] : new String[] {configFile});
            return;
        }
        try {
            Config config = configFile == null ? new Config() : new Config(Path.of(configFile));
            GradingRun.printResults(GradingRun.run(config, new GradingProgress()));
        } catch (Exception error) {
            System.err.println("error > " + error.getMessage());
            System.exit(1);
        }
    }

}

package app;

public class Main { //launches the terminal autograder
    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && args[0].startsWith("--"))) {
            System.err.println("usage: app.Main [config-file]");
            System.exit(1);
            return;
        }
        TuiMain.main(args);
    }
}

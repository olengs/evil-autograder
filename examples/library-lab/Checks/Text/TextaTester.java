public class TextaTester extends Texta {
    public static void main(String[] args) {
        int score = 0;
        System.out.println("Test 1: case 1");
        try {
            boolean passed = java.util.Objects.equals(countVowels(""), 0);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println("Test 2: case 2");
        try {
            boolean passed = java.util.Objects.equals(countVowels("library"), 2);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println("Test 3: case 3");
        try {
            boolean passed = java.util.Objects.equals(countVowels("AEIOU"), 5);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println("Test 4: case 4");
        try {
            boolean passed = java.util.Objects.equals(countVowels("rhythm"), 0);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println(score);
    }
}

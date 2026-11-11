public class InventoryTester extends Inventory {
    public static void main(String[] args) {
        int score = 0;
        System.out.println("Test 1: case 1");
        try {
            boolean passed = java.util.Objects.equals(totalAvailable(new int[]{}), 0);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println("Test 2: case 2");
        try {
            boolean passed = java.util.Objects.equals(totalAvailable(new int[]{2, 3}), 5);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println("Test 3: case 3");
        try {
            boolean passed = java.util.Objects.equals(totalAvailable(new int[]{-2, 4}), 4);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println("Test 4: case 4");
        try {
            boolean passed = java.util.Objects.equals(totalAvailable(new int[]{0, -1, -2}), 0);
            if (passed) { score++; System.out.println("Passed"); }
            else System.out.println("Failed");
        } catch (Exception error) { System.out.println("Failed -> " + error); }
        System.out.println(score);
    }
}

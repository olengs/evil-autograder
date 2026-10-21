package submission;

import java.util.Optional;

public class StudentDetails { //stores a student's username, name & email
    private final String username;
    private final String name;
    private final String email;

    public StudentDetails(String name, String username, String email) {
        this.name = name;
        this.username = username;
        this.email = email;
    }

    public static Optional<String> usernameFromEmail(String email) {
        if (email == null) return Optional.empty();
        String trimmed = email.strip();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0 || atIndex != trimmed.lastIndexOf('@') || atIndex == trimmed.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(trimmed.substring(0, atIndex));
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}

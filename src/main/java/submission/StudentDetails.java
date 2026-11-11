package submission;

/// stores a student's username, name & email
public class StudentDetails { 
    private final String username;
    private final String name;
    private final String email;

    public StudentDetails(String name, String username, String email) {
        this.name = name;
        this.username = username;
        this.email = email;
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

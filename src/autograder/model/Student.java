// Stores the roster identity used to identify submissions.
// Provides student name, username, and email details.

package autograder.model;

public class Student {
    private String userName;
    private String name;
    private String email;

    public Student(String name, String userName, String email) {
        this.name = name;
        this.userName = userName;
        this.email = email;
    }

    public String getUserName() {
        return userName;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}

//stores student name & username & email from the student list
//supports matching zip archives & checking answer headers & associating grades with scoresheet rows

package submission;

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

package autograder.model;

public class Student {

    private String userName;
    private String name;
    private String email;

    public Student(String name) {
        this.name = name;
    }

    public Student(String name, String userName) {
        this(name);
        this.userName = userName;
    }

    public Student(String name, String userName, String email) {
        this(name, userName);
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
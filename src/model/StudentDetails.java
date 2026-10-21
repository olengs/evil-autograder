package model;

public class StudentDetails {
    private String userName;
    private String name;
    private String email;

    public StudentDetails(String name, String userName, String email) {
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

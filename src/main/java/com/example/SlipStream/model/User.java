package com.example.SlipStream.model;

public class User {
    private String email;

    // Default constructor for Firebase/Jackson
    public User() {
    }

    public User(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "User{" +
               "email='" + email + '\'' +
               '}';
    }
}

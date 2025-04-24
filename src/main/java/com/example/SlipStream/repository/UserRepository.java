package com.example.SlipStream.repository;

import com.example.SlipStream.model.User;
import java.util.concurrent.ExecutionException;

public interface UserRepository {
    User getUserByEmail(String email) throws ExecutionException, InterruptedException;
    void saveUser(User user) throws ExecutionException, InterruptedException;
}

package com.example.SlipStream.service;

import com.example.SlipStream.repository.UserRepository; // Assuming you might want to check if user exists in your DB
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseUserDetailsService.class);
    private final UserRepository userRepository; // Optional: Inject if you want to load more user details/roles from your DB

    @Autowired
    public FirebaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Loading UserDetails for email: {}", email);
        // Here, we trust that the email comes from a verified Firebase token.
        // We create a UserDetails object on the fly.
        // Optionally, you could load additional details or roles from your UserRepository.

        // Example: Check if user exists in our DB (optional, but good practice)
        try {
            com.example.SlipStream.model.User appUser = userRepository.getUserByEmail(email); // Use injected repository
            if (appUser == null) {
                 logger.warn("User with email {} verified by Firebase but not found in local DB. Proceeding with basic details.", email);
                 // Decide whether to throw exception or proceed with basic details
                 // throw new UsernameNotFoundException("User not found in local database: " + email);
            } else {
                 logger.trace("User {} found in local DB.", email);
                 // You could load roles/authorities from appUser here if needed
            }
        } catch (ExecutionException | InterruptedException e) {
             logger.error("Error checking user existence in DB for email {}: {}", email, e.getMessage());
             Thread.currentThread().interrupt();
             // Decide how to handle DB errors during UserDetails loading
             // For now, we'll proceed with basic details, but you might want to throw an exception
        }

        // Create UserDetails with the email as username and a default role
        return new User(email, "", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

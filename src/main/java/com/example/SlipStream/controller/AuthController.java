package com.example.SlipStream.controller;

import com.example.SlipStream.security.FirebaseTokenFilter; // Need access to saveUserIfNotExists
import com.example.SlipStream.service.FirebaseUserDetailsService; // Import UserDetailsService
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest; // Import HttpServletRequest
import jakarta.servlet.http.HttpServletResponse; // Import HttpServletResponse
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.SecurityContextRepository; // Import SecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final FirebaseAuth firebaseAuth;
    private final FirebaseTokenFilter firebaseTokenFilter;
    private final FirebaseUserDetailsService userDetailsService; // Inject UserDetailsService
    private final SecurityContextRepository securityContextRepository; // Inject SecurityContextRepository

    @Autowired
    public AuthController(FirebaseAuth firebaseAuth,
                          FirebaseTokenFilter firebaseTokenFilter,
                          FirebaseUserDetailsService userDetailsService,
                          SecurityContextRepository securityContextRepository) {
        this.firebaseAuth = firebaseAuth;
        this.firebaseTokenFilter = firebaseTokenFilter;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(@RequestBody Map<String, String> payload, HttpServletRequest request, HttpServletResponse response) { // Add request/response
        String idToken = payload.get("idToken");
        if (idToken == null || idToken.isEmpty()) {
            return ResponseEntity.badRequest().body("ID token is required.");
        }
        logger.info("Received token verification request.");

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            logger.info("Firebase token verified for UID: {}, Email: {}", uid, email);

            if (email != null && !email.isEmpty()) {
                // 1. Save user to our DB if not exists (using the filter's method)
                logger.debug("Calling saveUserIfNotExists from AuthController for Email: {}", email);
                firebaseTokenFilter.saveUserIfNotExists(email);
                logger.info("User save process completed for {}", email);

                // 2. Establish Spring Security Session
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                // Create empty context and set authentication
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);

                // Explicitly save the context to the session repository
                securityContextRepository.saveContext(context, request, response);
                logger.info("Spring Security context set and saved to session for user: {}", email);

                return ResponseEntity.ok().body(Map.of("status", "success", "uid", uid, "email", email));
            } else {
                logger.warn("Token verified for UID {}, but email is missing or empty.", uid);
                return ResponseEntity.badRequest().body("Email missing or empty in token.");
            }
        } catch (FirebaseAuthException e) {
            logger.error("Firebase token verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Firebase token: " + e.getMessage());
        } catch (UsernameNotFoundException e) {
            logger.error("Failed to load UserDetails after Firebase verification for email {}: {}", payload.get("email"), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("User details could not be loaded after verification.");
        } catch (Exception e) {
            logger.error("Unexpected error during token verification or session creation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error during authentication.");
        }
    }
}

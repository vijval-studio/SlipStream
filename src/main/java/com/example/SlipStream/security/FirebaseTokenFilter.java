package com.example.SlipStream.security;

import com.example.SlipStream.model.User;
import com.example.SlipStream.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class FirebaseTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseTokenFilter.class);
    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    public FirebaseTokenFilter(FirebaseAuth firebaseAuth, UserRepository userRepository) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        logger.debug("FirebaseTokenFilter processing request for: {}", path);

        // --- Check for existing valid session authentication ---
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated() && !(existingAuth instanceof AnonymousAuthenticationToken)) {
            logger.info("User '{}' already authenticated via session for {}. Skipping Firebase token check.", existingAuth.getName(), path);
            filterChain.doFilter(request, response);
            logger.debug("Filter chain completed for {} (session auth existed)", path);
            return;
        } else {
            logger.debug("No valid pre-existing session authentication found for {}. Current context auth: {}", path, existingAuth);
        }
        // --- End session check ---

        if (path.equals("/api/auth/verify-token") || path.equals("/login") || path.equals("/")) {
            logger.trace("Skipping FirebaseTokenFilter token logic for path: {}", path);
            filterChain.doFilter(request, response);
            logger.debug("Filter chain completed for {} (skipped path)", path);
            return;
        }

        logger.debug("Attempting Firebase token verification (as session auth was not found/valid) for path: {}", path);
        String authorizationHeader = request.getHeader("Authorization");
        String idToken = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            idToken = authorizationHeader.substring(7);
            logger.debug("Found Bearer token in Authorization header for {}.", path);
        } else {
            logger.debug("No Bearer token found in Authorization header for {}. Relying on session mechanism (which seems to have failed).", path);
        }

        FirebaseToken decodedToken = null;
        if (idToken != null) {
            try {
                decodedToken = firebaseAuth.verifyIdToken(idToken);
                logger.debug("Firebase token verified successfully for UID: {} for path {}", decodedToken.getUid(), path);
            } catch (FirebaseAuthException e) {
                logger.warn("Firebase token verification failed for path {}: {}", path, e.getMessage());
                SecurityContextHolder.clearContext();
                logger.debug("Cleared SecurityContext due to invalid token for {}", path);
            }
        }

        if (decodedToken != null) {
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            logger.debug("Token details - UID: {}, Email: {}", uid, email);

            if (email != null && !email.isEmpty()) {
                if (SecurityContextHolder.getContext().getAuthentication() == null ||
                    !SecurityContextHolder.getContext().getAuthentication().isAuthenticated() ||
                    SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken) {

                    logger.info("Valid Firebase token found for {}. Setting SecurityContext for user: {}", path, email);

                    UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                            email,
                            "",
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.info("Successfully set SecurityContext via token for user '{}' for path {}", email, path);
                } else {
                    logger.warn("Valid token found for {}, but SecurityContext already contained auth: {}. Not overwriting.", path, SecurityContextHolder.getContext().getAuthentication().getName());
                }

            } else {
                logger.warn("Firebase token for UID '{}' (path {}) does not contain a valid email. Clearing context.", uid, path);
                SecurityContextHolder.clearContext();
            }
        } else {
            logger.debug("No valid decoded token processed for path: {}. Proceeding chain.", path);
        }

        logger.debug("Proceeding filter chain for {}", path);
        filterChain.doFilter(request, response);
        logger.debug("Filter chain completed for {}", path);
    }

    public void saveUserIfNotExists(String email) {
        if (email == null || email.isEmpty()) {
            logger.warn("saveUserIfNotExists called with null or empty email.");
            return;
        }
        logger.debug("Inside saveUserIfNotExists for Email: {}", email);
        try {
            logger.debug("Checking if user exists by email: {}", email);
            User existingUser = userRepository.getUserByEmail(email);
            if (existingUser == null) {
                logger.info("User with email {} not found. Creating new user entry.", email);
                User newUser = new User(email);
                userRepository.saveUser(newUser);
                logger.info("Successfully initiated save for new user: {}", email);
            } else {
                logger.debug("User with email {} already exists in Firestore. No action needed.", email);
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error accessing or saving user data in Firestore for email {}: {}", email, e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error during saveUserIfNotExists for email {}: {}", email, e.getMessage(), e);
        }
    }
}

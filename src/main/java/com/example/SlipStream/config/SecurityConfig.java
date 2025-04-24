package com.example.SlipStream.config;

import com.example.SlipStream.security.FirebaseTokenFilter; // Import FirebaseTokenFilter
import com.google.firebase.auth.FirebaseAuth; // Import FirebaseAuth
import com.example.SlipStream.repository.UserRepository; // Import UserRepository
import com.example.SlipStream.service.FirebaseUserDetailsService; // Import UserDetailsService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Use AbstractHttpConfigurer for csrf
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory; // Import LoggerFactory
import java.net.URLEncoder; // Import URLEncoder
import java.nio.charset.StandardCharsets; // Correct package for StandardCharsets
import jakarta.servlet.http.HttpServletResponse; // Import HttpServletResponse

import java.util.List; // Import List

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseUserDetailsService firebaseUserDetailsService;
    private final FirebaseAuth firebaseAuth; // Needed to create the filter bean
    private final UserRepository userRepository; // Needed to create the filter bean

    @Autowired
    public SecurityConfig(FirebaseUserDetailsService firebaseUserDetailsService,
                          FirebaseAuth firebaseAuth, // Inject FirebaseAuth
                          UserRepository userRepository) { // Inject UserRepository
        this.firebaseUserDetailsService = firebaseUserDetailsService;
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
    }

    // Declare FirebaseTokenFilter as a Bean
    @Bean
    public FirebaseTokenFilter firebaseTokenFilter() {
        return new FirebaseTokenFilter(firebaseAuth, userRepository);
    }

    // Bean for SecurityContextRepository
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   FirebaseTokenFilter firebaseTokenFilter) throws Exception {
        http
            // Disable CSRF - common for APIs, ensure state-changing operations are protected otherwise (e.g., idempotent PUT/DELETE)
            .csrf(AbstractHttpConfigurer::disable)
            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Session management: Use sessions IF REQUIRED (needed for SecurityContextRepository)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // Set the SecurityContextRepository to manage context between requests (using HttpSession)
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Publicly accessible static resources and basic pages
                .requestMatchers("/", "/error", "/login", "/favicon.ico", "/*.png", "/*.gif", "/*.svg", "/*.jpg", "/*.html", "/*.css", "/*.js").permitAll()
                // Authentication endpoint
                .requestMatchers("/api/auth/verify-token").permitAll()
                // WebSocket endpoint - Handled by Spring Security WebSocket integration, typically requires authentication established before connection
                // Permit all here allows the connection attempt, auth is often checked at STOMP level or in @MessageMapping
                .requestMatchers("/ws/**").permitAll()
                // Public viewing/API access (GET requests) - Access control done in Service layer
                .requestMatchers(HttpMethod.GET, "/view/pages/**").permitAll() // View access controlled by PageService
                .requestMatchers(HttpMethod.GET, "/api/pages/**").permitAll()   // API read access controlled by PageService
                // Authenticated access required for dashboard and specific view actions
                .requestMatchers("/dashboard").authenticated()
                .requestMatchers("/view/pages/create/**", "/view/pages/edit/**").authenticated() // Creating/Editing requires login
                // Authenticated access for all other API endpoints (POST, PUT, DELETE on /api/pages/*)
                .requestMatchers("/api/**").authenticated()
                .requestMatchers(HttpMethod.GET,"/workspaces/**").authenticated() // All workspace-related API endpoints require authentication
                // Any other request not matched above requires authentication
                .anyRequest().authenticated()
            )
            // Configure UserDetailsService for loading user details during session authentication
            .userDetailsService(firebaseUserDetailsService)
            // Add the Firebase token filter *before* the standard username/password filter
            .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class)
            // Exception handling for authentication entry point and access denied
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    String continueUrl = request.getRequestURI();
                    if (request.getQueryString() != null) {
                        continueUrl += "?" + request.getQueryString();
                    }
                    if (!request.getRequestURI().equals("/login")) {
                        logger.info("Authentication required for {}. Redirecting to login.", request.getRequestURI());
                        response.sendRedirect("/login?continue=" + URLEncoder.encode(continueUrl, StandardCharsets.UTF_8.toString()));
                    } else {
                        logger.warn("Authentication exception occurred on /login page itself: {}", authException.getMessage());
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    logger.warn("Access Denied for {}: {}", request.getRequestURI(), accessDeniedException.getMessage());
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
                })
            );

        return http.build();
    }

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow specific origins (more secure than "*")
        configuration.setAllowedOrigins(List.of("http://localhost:8080", "http://127.0.0.1:8080")); // Use List.of
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Use List.of
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type")); // Use List.of
        configuration.setAllowCredentials(true); // Allow cookies/credentials
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply CORS to all paths
        return source;
    }
}
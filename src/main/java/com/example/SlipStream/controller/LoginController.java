package com.example.SlipStream.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage() {
        // If user is already authenticated, maybe redirect them away from login page
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            // Redirect to a default logged-in page, e.g., dashboard or last visited page
            // return "redirect:/"; // Example redirect to home
        }
        return "login"; // Return the name of the login template (login.html)
    }
}

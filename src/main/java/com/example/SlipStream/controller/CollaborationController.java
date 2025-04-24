package com.example.SlipStream.controller;

import com.example.SlipStream.service.PageService; // Assuming PageService needed for access checks
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent; // Import disconnect event
import org.springframework.context.event.EventListener; // Import event listener

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;

@Controller
public class CollaborationController {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationController.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final PageService pageService; // Inject PageService for access checks

    // In-memory store for presence: Map<PageID, Set<UserInfo>>
    private final Map<String, Set<UserInfo>> activeUsersByPage = new ConcurrentHashMap<>();

    @Autowired
    public CollaborationController(SimpMessagingTemplate messagingTemplate, PageService pageService) {
        this.messagingTemplate = messagingTemplate;
        this.pageService = pageService;
    }

    @MessageMapping("/page/{pageId}/join")
    public void handleJoin(@DestinationVariable String pageId, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Anonymous user attempted to join page {}", pageId);
            // Optionally send an error back to the specific user session
            return;
        }
        String userEmail = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        // *** Access Check ***
        try {
            pageService.getPage(pageId); // Use getPage to check if user has at least view access
            logger.debug("User {} has access to join page {}", userEmail, pageId);
        } catch (AccessDeniedException e) {
            logger.warn("Access Denied: User {} attempted to join page {} without permission.", userEmail, pageId);
            // Optionally send an error message back to the user's session
            // messagingTemplate.convertAndSendToUser(userEmail, "/queue/errors", "Access denied for page " + pageId);
            return; // Do not proceed if access is denied
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error checking access for user {} joining page {}: {}", userEmail, pageId, e.getMessage());
            // Optionally send an error message back
            return; // Do not proceed on error
        }

        UserInfo newUser = new UserInfo(userEmail, sessionId);
        Set<UserInfo> usersOnPage = activeUsersByPage.computeIfAbsent(pageId, k -> new CopyOnWriteArraySet<>());

        if (usersOnPage.add(newUser)) {
            logger.info("User '{}' (Session: {}) joined page {}", userEmail, sessionId, pageId);
            broadcastPresence(pageId);
        } else {
             logger.debug("User '{}' (Session: {}) re-joined or already present on page {}", userEmail, sessionId, pageId);
             // Optionally re-broadcast presence if needed, e.g., if connection was temporarily lost
             broadcastPresence(pageId);
        }

        // Store pageId in session attributes for disconnect handling
        headerAccessor.getSessionAttributes().put("pageId", pageId);
        headerAccessor.getSessionAttributes().put("userEmail", userEmail); // Store email too
    }

    // No explicit @MessageMapping("/page/{pageId}/leave") needed if using disconnect event

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String pageId = (String) headerAccessor.getSessionAttributes().get("pageId");
        String userEmail = (String) headerAccessor.getSessionAttributes().get("userEmail"); // Retrieve email

        if (pageId != null && sessionId != null && userEmail != null) {
            Set<UserInfo> usersOnPage = activeUsersByPage.get(pageId);
            if (usersOnPage != null) {
                // Remove user based on sessionId
                boolean removed = usersOnPage.removeIf(userInfo -> sessionId.equals(userInfo.getSessionId()));
                if (removed) {
                    logger.info("User '{}' (Session: {}) disconnected from page {}", userEmail, sessionId, pageId);
                    broadcastPresence(pageId);

                    // Optional: Clean up map entry if page becomes empty
                    if (usersOnPage.isEmpty()) {
                        activeUsersByPage.remove(pageId);
                        logger.debug("Page {} is now empty, removed from active presence tracking.", pageId);
                    }
                } else {
                     logger.warn("Session {} disconnected, but user was not found in active list for page {}.", sessionId, pageId);
                }
            }
        } else {
             logger.debug("Session {} disconnected without pageId/userEmail attribute, likely wasn't on a page.", sessionId);
        }
    }


    private void broadcastPresence(String pageId) {
        Set<UserInfo> users = activeUsersByPage.getOrDefault(pageId, Set.of());
        String destination = "/topic/pages/" + pageId + "/presence";
        logger.debug("Broadcasting presence update for page {}: {} users", pageId, users.size());
        messagingTemplate.convertAndSend(destination, users);
    }

    // Placeholder for UserInfo class (adjust if needed)
    public static class UserInfo {
        private String email;
        private String sessionId; // Added to track connection

        // Default constructor for JSON deserialization
        public UserInfo() {}

        // Constructor used when adding user
        public UserInfo(String email, String sessionId) {
            this.email = email;
            this.sessionId = sessionId;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        // equals and hashCode based on sessionId for uniqueness within a page's set
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserInfo userInfo = (UserInfo) o;
            return java.util.Objects.equals(sessionId, userInfo.sessionId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(sessionId);
        }

         @Override
         public String toString() {
             return "UserInfo{" +
                    "email='" + email + '\'' +
                    ", sessionId='" + sessionId + '\'' +
                    '}';
         }
    }

    // Placeholder for potential future cursor position handling
    @MessageMapping("/page/{pageId}/cursor")
    public void handleCursor(@DestinationVariable String pageId, @Payload CursorPosition cursorPosition, Principal principal) {
        if (principal == null) return;
        String userEmail = principal.getName();
        cursorPosition.setUserEmail(userEmail); // Add user email to the payload

        // Add access check if needed - only users with edit access should send cursor updates?
        // try { pageService.getPageForEditing(pageId); } catch (...) { return; }

        String destination = "/topic/pages/" + pageId + "/cursors";
        // Avoid broadcasting back to the sender if possible, or let frontend handle it
        messagingTemplate.convertAndSend(destination, cursorPosition);
        // logger.trace("Relayed cursor position for user {} on page {}", userEmail, pageId);
    }

    // DTO for cursor position
    public static class CursorPosition {
        private String userEmail; // Set by server based on Principal
        private int position; // Example: character index or block ID + offset
        // Add other relevant fields like selection range

        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
    }
}

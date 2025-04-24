package com.example.SlipStream.service.observer;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component; // Make it a Spring component

import com.example.SlipStream.model.PageComponent;

/**
 * Concrete observer that sends page updates via WebSocket.
 */
@Component // Register as a Spring Bean
public class WebSocketPageObserver implements PageObserver {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketPageObserver.class);
    private final SimpMessagingTemplate messagingTemplate;

    // Inject SimpMessagingTemplate via constructor
    public WebSocketPageObserver(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void update(PageComponent page) {
        String pageId = page.getPageId();
        String destination = "/topic/pages/" + pageId;
        try {
            // Construct the payload exactly as before
            Map<String, Object> updatePayload = Map.of(
                "pageId", page.getPageId(),
                "title", page.getTitle(),
                "content", page.getContent(), // Ensure getContent() returns appropriate summary/content
                "lastUpdated", page.getLastUpdated(),
                "isPublished", page.isPublished(),
                "sharingInfo", page.getSharingInfo()
                // Add other relevant fields if needed
            );
            logger.info("Broadcasting update for page {} to WebSocket destination {}", pageId, destination);
            messagingTemplate.convertAndSend(destination, updatePayload);
        } catch (Exception e) {
            logger.error("Error broadcasting update via WebSocket for page {}: {}", pageId, e.getMessage(), e);
        }
    }
}

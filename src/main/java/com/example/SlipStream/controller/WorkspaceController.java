package com.example.SlipStream.controller;

import com.example.SlipStream.model.PageComponent;
import com.example.SlipStream.model.Workspace;
import com.example.SlipStream.service.PageService;
import com.example.SlipStream.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping("/workspaces")
public class WorkspaceController {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;
    private final PageService pageService;

    @Autowired
    public WorkspaceController(WorkspaceService workspaceService, PageService pageService) {
        this.workspaceService = workspaceService;
        this.pageService = pageService;
    }

    // Helper method to get current user email
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal().toString())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else {
                return authentication.getName();
            }
        }
        return null;
    }

    // --- Workspace Listing ---

    @GetMapping
    public String listWorkspaces(Model model) {
        return "redirect:/dashboard";
    }

    // --- View Single Workspace ---

    @GetMapping("/{workspaceId}")
    public String viewWorkspace(@PathVariable String workspaceId, Model model, RedirectAttributes redirectAttributes) {
        String currentUserEmail = getCurrentUserEmail();
        if (currentUserEmail == null) return "redirect:/login";

        try {
            Workspace workspace = workspaceService.getWorkspaceByIdForMember(workspaceId, currentUserEmail);
            if (workspace == null) {
                logger.warn("User {} attempted to access non-existent or unauthorized workspace {}", currentUserEmail, workspaceId);
                redirectAttributes.addFlashAttribute("errorMessage", "Workspace not found or access denied.");
                return "redirect:/dashboard";
            }

            List<PageComponent> rootPages = new ArrayList<>();
            if (workspace.getRootPageIds() != null && !workspace.getRootPageIds().isEmpty()) {
                rootPages = pageService.getPagesByIds(workspace.getRootPageIds());
                rootPages.sort(Comparator.comparing(p -> p.getTitle() != null ? p.getTitle() : "", String.CASE_INSENSITIVE_ORDER));
            }

            model.addAttribute("workspace", workspace);
            model.addAttribute("rootPages", rootPages);
            model.addAttribute("currentUserEmail", currentUserEmail);
            return "workspace_detail";
        } catch (AccessDeniedException e) {
            logger.warn("Access Denied: User '{}' attempted to view workspace '{}'.", currentUserEmail, workspaceId);
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied to this workspace.");
            return "redirect:/dashboard";
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error fetching workspace details for {}: {}", workspaceId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading workspace details.");
            return "redirect:/dashboard";
        } catch (Exception e) {
            logger.error("Unexpected error viewing workspace {}: {}", workspaceId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred.");
            return "redirect:/dashboard";
        }
    }

    // --- Create Workspace ---

    @PostMapping
    public String createWorkspace(@RequestParam String name, RedirectAttributes redirectAttributes) {
        String ownerEmail = getCurrentUserEmail();
        if (ownerEmail == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be logged in to create a workspace.");
            return "redirect:/login";
        }
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Workspace name cannot be empty.");
            return "redirect:/dashboard";
        }

        try {
            Workspace newWorkspace = new Workspace(name.trim(), ownerEmail);
            workspaceService.createWorkspace(newWorkspace);
            redirectAttributes.addFlashAttribute("successMessage", "Workspace '" + name.trim() + "' created successfully.");
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error creating workspace for user {}: {}", ownerEmail, e.getMessage(), e);
            Thread.currentThread().interrupt();
            redirectAttributes.addFlashAttribute("errorMessage", "Server error occurred while creating workspace.");
        } catch (Exception e) {
            logger.error("Unexpected error creating workspace: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred.");
        }
        return "redirect:/dashboard";
    }

    // --- Delete Workspace ---

    @DeleteMapping("/{workspaceId}")
    @ResponseBody
    public ResponseEntity<?> deleteWorkspace(@PathVariable String workspaceId) {
        String currentUserEmail = getCurrentUserEmail();
        if (currentUserEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not authenticated"));
        }

        try {
            boolean deleted = workspaceService.deleteWorkspace(workspaceId, currentUserEmail);
            if (deleted) {
                logger.info("Workspace {} deleted by user {}", workspaceId, currentUserEmail);
                return ResponseEntity.ok(Map.of("message", "Workspace deleted successfully"));
            } else {
                logger.warn("Workspace {} not deleted (user {} might not be owner or workspace not found)", workspaceId, currentUserEmail);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Could not delete workspace. You might not be the owner or the workspace does not exist."));
            }
        } catch (AccessDeniedException e) {
            logger.warn("Access Denied: User {} attempted to delete workspace {}", currentUserEmail, workspaceId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access Denied. Only the owner can delete the workspace."));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error deleting workspace {}: {}", workspaceId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error occurred during deletion."));
        } catch (IllegalArgumentException e) {
            logger.warn("Error deleting workspace {}: {}", workspaceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error deleting workspace {}: {}", workspaceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    // --- Add Member to Workspace ---
    @PostMapping("/{workspaceId}/members")
    @ResponseBody
    public ResponseEntity<?> addMemberToWorkspace(@PathVariable String workspaceId, @RequestBody Map<String, String> payload) {
        String currentUserEmail = getCurrentUserEmail();
        if (currentUserEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not authenticated"));
        }

        String memberEmail = payload.get("email");
        if (memberEmail == null || memberEmail.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Member email cannot be empty"));
        }
        memberEmail = memberEmail.trim();

        try {
            boolean added = workspaceService.addMember(workspaceId, memberEmail);
            if (added) {
                logger.info("Member {} added to workspace {} by user {}", memberEmail, workspaceId, currentUserEmail);
                Workspace ws = workspaceService.getWorkspaceById(workspaceId);
                if (ws != null && ws.getMembers().contains(memberEmail)) {
                    return ResponseEntity.ok(Map.of("message", "Member '" + memberEmail + "' added or already present in the workspace."));
                } else {
                    logger.warn("Member {} reported as added to workspace {} but not found in member list.", memberEmail, workspaceId);
                    return ResponseEntity.ok(Map.of("message", "Member '" + memberEmail + "' processed."));
                }
            } else {
                logger.warn("Failed to add member {} to workspace {} (user {}) - service returned false.", memberEmail, workspaceId, currentUserEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Could not add member. Workspace might not exist or another issue occurred."));
            }
        } catch (SecurityException | AccessDeniedException e) {
            logger.warn("Access Denied: User {} attempted to add member {} to workspace {}: {}", currentUserEmail, memberEmail, workspaceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access Denied. " + e.getMessage()));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error adding member {} to workspace {}: {}", memberEmail, workspaceId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error occurred while adding member."));
        } catch (IllegalArgumentException e) {
            logger.warn("Error adding member {} to workspace {}: {}", memberEmail, workspaceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error adding member {} to workspace {}: {}", memberEmail, workspaceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }
}

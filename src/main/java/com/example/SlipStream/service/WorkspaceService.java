package com.example.SlipStream.service;

import com.example.SlipStream.model.PageComponent;
import com.example.SlipStream.model.Workspace;
import com.example.SlipStream.repository.PageRepository;
import com.example.SlipStream.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * WorkspaceService acts as a Facade for workspace-related operations.
 * It simplifies interactions with the WorkspaceRepository and potentially
 * other repositories (like PageRepository for cascading deletes or linking).
 */
@Service
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final PageRepository pageRepository;
    private final PageService pageService;

    public WorkspaceService(WorkspaceRepository workspaceRepository, PageRepository pageRepository, PageService pageService) {
        this.workspaceRepository = workspaceRepository;
        this.pageRepository = pageRepository;
        this.pageService = pageService;
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null && !"anonymousUser".equals(authentication.getPrincipal().toString())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else {
                logger.warn("Authenticated user principal is not UserDetails in service, attempting getName(): {}", principal.getClass().getName());
                return authentication.getName();
            }
        }
        logger.warn("Could not determine current user email from SecurityContextHolder in WorkspaceService.");
        return null;
    }

    public String createWorkspace(Workspace workspace) throws ExecutionException, InterruptedException {
        if (workspace.getOwner() == null || workspace.getOwner().isEmpty()) {
            throw new IllegalStateException("Workspace owner must be set before calling createWorkspace service method.");
        }
        logger.info("Creating workspace '{}' for owner {}", workspace.getName(), workspace.getOwner());
        if (workspace.getId() == null || workspace.getId().isEmpty()) {
            workspace.setId("ws_" + java.util.UUID.randomUUID().toString());
            logger.warn("Workspace ID was missing, generated: {}", workspace.getId());
        }
        if (workspace.getMembers() == null || !workspace.getMembers().contains(workspace.getOwner())) {
            if (workspace.getMembers() == null) workspace.setMembers(new ArrayList<>());
            workspace.getMembers().add(workspace.getOwner());
            logger.debug("Added owner {} to members list for workspace {}", workspace.getOwner(), workspace.getId());
        }

        return workspaceRepository.createWorkspace(workspace);
    }

    public Workspace getWorkspaceById(String workspaceId) throws ExecutionException, InterruptedException {
        String currentUser = getCurrentUserEmail();
        if (currentUser == null) {
            throw new SecurityException("User not authenticated.");
        }
        Workspace ws = workspaceRepository.getWorkspaceById(workspaceId);
        if (ws != null && !ws.getMembers().contains(currentUser)) {
            logger.warn("User {} attempted to access workspace {} without being a member.", currentUser, workspaceId);
            throw new SecurityException("User does not have access to this workspace.");
        }
        return ws;
    }

    public Workspace getWorkspaceByIdForMember(String workspaceId, String userEmail) throws ExecutionException, InterruptedException {
        Workspace workspace = workspaceRepository.getWorkspaceById(workspaceId);
        if (workspace != null && workspace.getMembers() != null && workspace.getMembers().contains(userEmail)) {
            return workspace;
        }
        logger.warn("User {} attempted to access workspace {} but is not a member or workspace doesn't exist.", userEmail, workspaceId);
        throw new AccessDeniedException("Access denied to workspace " + workspaceId);
    }

    public List<Workspace> getWorkspacesForCurrentUser() throws ExecutionException, InterruptedException {
        String currentUser = getCurrentUserEmail();
        if (currentUser == null) {
            throw new IllegalStateException("User not authenticated.");
        }
        return workspaceRepository.getWorkspacesByMember(currentUser);
    }

    public List<Workspace> getWorkspacesForUser(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Service: Getting workspaces for user {}", userEmail);
        return workspaceRepository.getWorkspacesForUser(userEmail);
    }

    public boolean updateWorkspaceName(String workspaceId, String newName) throws ExecutionException, InterruptedException {
        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace == null) {
            return false;
        }
        workspace.setName(newName);
        return workspaceRepository.updateWorkspace(workspace);
    }

    public boolean addMember(String workspaceId, String memberEmail) throws ExecutionException, InterruptedException {
        Workspace workspace = workspaceRepository.getWorkspaceById(workspaceId);
        if (workspace == null) {
            logger.error("Workspace not found: {}", workspaceId);
            return false;
        }
        String currentUser = getCurrentUserEmail();
        if (currentUser == null || !workspace.getOwner().equals(currentUser)) {
            logger.warn("User {} attempted to add member to workspace {} without being the owner.", currentUser, workspaceId);
            throw new SecurityException("Only the workspace owner can add members.");
        }
        if (workspace.getMembers().contains(memberEmail)) {
            logger.info("User {} is already a member of workspace {}", memberEmail, workspaceId);
            return true;
        }

        boolean memberAddedToWorkspace = workspaceRepository.addMemberToWorkspace(workspaceId, memberEmail);

        if (memberAddedToWorkspace) {
            logger.info("Successfully added member {} to workspace {}.", memberEmail, workspaceId);
        } else {
            logger.warn("Failed to add member {} to workspace {} via repository.", memberEmail, workspaceId);
        }
        return memberAddedToWorkspace;
    }

    public boolean removeMember(String workspaceId, String memberEmail) throws ExecutionException, InterruptedException {
        Workspace workspace = workspaceRepository.getWorkspaceById(workspaceId);
        if (workspace == null) {
            logger.error("Workspace not found: {}", workspaceId);
            return false;
        }
        String currentUser = getCurrentUserEmail();
        if (currentUser == null) {
            throw new IllegalStateException("User not authenticated.");
        }
        if (workspace.getOwner().equals(memberEmail)) {
            logger.error("Cannot remove the owner {} from workspace {}", memberEmail, workspaceId);
            return false;
        }
        if (!workspace.getOwner().equals(currentUser) && !currentUser.equals(memberEmail)) {
            logger.warn("User {} attempted to remove member {} from workspace {} without permission.", currentUser, memberEmail, workspaceId);
            throw new SecurityException("User does not have permission to remove this member.");
        }
        boolean removed = workspaceRepository.removeMemberFromWorkspace(workspaceId, memberEmail);
        if (removed) {
            logger.info("Member {} removed from workspace {}.", memberEmail, workspaceId);
        }
        return removed;
    }

    public boolean deleteWorkspaceAndAssociatedPages(String workspaceId) throws ExecutionException, InterruptedException {
        Workspace workspace = workspaceRepository.getWorkspaceById(workspaceId);
        if (workspace == null) {
            logger.error("Workspace not found: {}", workspaceId);
            return false;
        }
        String currentUser = getCurrentUserEmail();
        if (currentUser == null || !workspace.getOwner().equals(currentUser)) {
            logger.warn("User {} attempted to delete workspace {} without being the owner.", currentUser, workspaceId);
            throw new SecurityException("Only the workspace owner can delete the workspace.");
        }
        List<String> rootPageIds = workspace.getRootPageIds();
        if (rootPageIds != null && !rootPageIds.isEmpty()) {
            logger.info("Deleting {} root pages for workspace {}", rootPageIds.size(), workspaceId);
            for (String pageId : rootPageIds) {
                try {
                    boolean deleted = pageRepository.deletePage(pageId);
                    if (!deleted) {
                        logger.warn("Failed to delete page {} during workspace {} deletion (returned false)", pageId, workspaceId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to delete page {} during workspace {} deletion", pageId, workspaceId, e);
                }
            }
        } else {
            logger.info("No root pages found to delete for workspace {}", workspaceId);
        }
        return workspaceRepository.deleteWorkspace(workspaceId);
    }

    public boolean deleteWorkspace(String workspaceId, String currentUserEmail) throws ExecutionException, InterruptedException {
        Workspace workspace = workspaceRepository.getWorkspaceById(workspaceId);
        if (workspace == null) {
            logger.warn("Attempted to delete non-existent workspace: {}", workspaceId);
            throw new IllegalArgumentException("Workspace not found.");
        }

        if (!workspace.getOwner().equals(currentUserEmail)) {
            logger.warn("User {} attempted to delete workspace {} owned by {}", currentUserEmail, workspaceId, workspace.getOwner());
            throw new AccessDeniedException("Only the workspace owner can delete it.");
        }

        if (workspace.getRootPageIds() != null && !workspace.getRootPageIds().isEmpty()) {
            logger.info("Deleting {} root pages for workspace {}", workspace.getRootPageIds().size(), workspaceId);
            for (String pageId : workspace.getRootPageIds()) {
                try {
                    pageService.deletePage(pageId);
                    logger.debug("Deleted page {} during workspace {} deletion.", pageId, workspaceId);
                } catch (Exception e) {
                    logger.error("Error deleting page {} during workspace {} deletion: {}", pageId, workspaceId, e.getMessage(), e);
                }
            }
        }

        return workspaceRepository.deleteWorkspace(workspaceId);
    }

    public boolean addRootPageToWorkspace(String workspaceId, String pageId) throws ExecutionException, InterruptedException {
        Workspace workspace = getWorkspaceById(workspaceId);
        PageComponent page = pageRepository.getPage(pageId);
        if (workspace == null || page == null) {
            logger.error("Workspace {} or Page {} not found for linking.", workspaceId, pageId);
            return false;
        }
        String currentUser = getCurrentUserEmail();
        boolean canEditPage = page.getOwner().equals(currentUser) || (page.getSharingInfo() != null && "edit".equals(page.getSharingInfo().get(currentUser)));
        if (!canEditPage) {
            logger.warn("User {} attempted to add page {} to workspace {} without edit rights on the page.", currentUser, pageId, workspaceId);
            throw new SecurityException("User must have edit permission on the page to add it to a workspace.");
        }
        if (page.getParentPageId() != null && !page.getParentPageId().isEmpty()) {
            logger.warn("Page {} already has a parent {}, cannot add as root page to workspace {}", pageId, page.getParentPageId(), workspaceId);
            return false;
        }
        return workspaceRepository.addRootPageToWorkspace(workspaceId, pageId);
    }

    public boolean removeRootPageFromWorkspace(String workspaceId, String pageId) throws ExecutionException, InterruptedException {
        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace == null) return false;
        String currentUser = getCurrentUserEmail();
        PageComponent page = pageRepository.getPage(pageId);
        if (page != null) {
            boolean canEditPage = page.getOwner().equals(currentUser) || (page.getSharingInfo() != null && "edit".equals(page.getSharingInfo().get(currentUser)));
            if (!canEditPage) {
                logger.warn("User {} attempted to remove page {} from workspace {} without edit rights on the page.", currentUser, pageId, workspaceId);
                throw new SecurityException("User must have edit permission on the page to remove it from a workspace.");
            }
        } else {
            logger.warn("Page {} not found while attempting to remove from workspace {}. Proceeding with removal from workspace list.", pageId, workspaceId);
        }
        return workspaceRepository.removeRootPageFromWorkspace(workspaceId, pageId);
    }

    public List<PageComponent> getRootPagesForWorkspace(String workspaceId) throws ExecutionException, InterruptedException {
        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace == null) {
            return List.of();
        }
        String currentUser = getCurrentUserEmail();
        List<String> rootPageIds = workspace.getRootPageIds();
        if (rootPageIds == null || rootPageIds.isEmpty()) {
            return List.of();
        }
        List<PageComponent> accessibleRootPages = new ArrayList<>();
        for (String pageId : rootPageIds) {
            try {
                PageComponent page = pageRepository.getPage(pageId);
                if (page != null) {
                    boolean canViewPage = page.isPublished()
                            || page.getOwner().equals(currentUser)
                            || (page.getSharingInfo() != null && page.getSharingInfo().containsKey(currentUser));
                    if (canViewPage) {
                        accessibleRootPages.add(page);
                    } else {
                        logger.debug("User {} cannot view page {} within workspace {}", currentUser, pageId, workspaceId);
                    }
                } else {
                    logger.warn("Root page ID {} listed in workspace {} but page not found.", pageId, workspaceId);
                }
            } catch (Exception e) {
                logger.error("Error fetching root page {} for workspace {}", pageId, workspaceId, e);
            }
        }
        return accessibleRootPages;
    }
}

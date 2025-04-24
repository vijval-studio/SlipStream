package com.example.SlipStream.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import java.util.Map;
import java.util.Set;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.example.SlipStream.model.ContainerPage;
import com.example.SlipStream.model.ContentPage;
import com.example.SlipStream.model.PageComponent;
import com.example.SlipStream.model.Workspace;
import com.example.SlipStream.repository.PageRepository;

import com.example.SlipStream.repository.WorkspaceRepository;

// Add imports for observer classes
import com.example.SlipStream.service.observer.PageSubject;
import com.example.SlipStream.service.observer.PageSubjectManager;

import org.springframework.beans.factory.annotation.Qualifier;

import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.FieldPath;

@Service
public class PageService {

    private static final Logger logger = LoggerFactory.getLogger(PageService.class);
    private final PageRepository pageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WorkspaceRepository workspaceRepository;
    private final PageSubjectManager subjectManager; // Add subjectManager field

    @Autowired
    public PageService(PageRepository pageRepository, SimpMessagingTemplate messagingTemplate, @Qualifier("firebaseWorkspaceRepository") WorkspaceRepository workspaceRepository, PageSubjectManager subjectManager) { // Add subjectManager to constructor
        this.pageRepository = pageRepository;
        this.messagingTemplate = messagingTemplate;
        this.workspaceRepository = workspaceRepository;
        this.subjectManager = subjectManager; // Assign subjectManager

    }

    public String createContentPage(String title, String content, String parentPageId, String owner, String workspaceId)
            throws ExecutionException, InterruptedException {
        String pageOwner = (owner != null && !owner.isEmpty()) ? owner : getCurrentUserEmail();
        if (pageOwner == null) {
            throw new IllegalStateException("Cannot create page without an authenticated owner.");
        }
        ContentPage page = new ContentPage(title, content, parentPageId, pageOwner);
        String pageId = pageRepository.createPage(page);
        page.setPageId(pageId);
        logger.info("Created Content Page: ID={}, Title='{}', Owner={}", pageId, title, pageOwner);

        if (workspaceId != null && !workspaceId.isEmpty()) {
            boolean addedToWorkspace = workspaceRepository.addRootPageToWorkspace(workspaceId, pageId);
            if (!addedToWorkspace) {
                logger.warn("Failed to add page {} to workspace {}", pageId, workspaceId);
            } else {
                logger.info("Successfully added page {} to workspace {}", pageId, workspaceId);
            }
        } else if (parentPageId == null) {
            logger.warn("Root page {} created without a workspace association.", pageId);
        }

        if (parentPageId != null && !parentPageId.isEmpty()) {
            updateParentChildRelationship(parentPageId, pageId);
        }

        return pageId;
    }

    public String createContainerPage(String title, String summary, String parentPageId, String owner, String workspaceId)
            throws ExecutionException, InterruptedException {
        String pageOwner = (owner != null && !owner.isEmpty()) ? owner : getCurrentUserEmail();
        if (pageOwner == null) {
            throw new IllegalStateException("Cannot create page without an authenticated owner.");
        }
        ContainerPage page = new ContainerPage(title, summary, parentPageId, pageOwner);
        String pageId = pageRepository.createPage(page);
        page.setPageId(pageId);
        logger.info("Created Container Page: ID={}, Title='{}', Owner={}", pageId, title, pageOwner);

        if (workspaceId != null && !workspaceId.isEmpty()) {
            boolean addedToWorkspace = workspaceRepository.addRootPageToWorkspace(workspaceId, pageId);
            if (!addedToWorkspace) {
                logger.warn("Failed to add page {} to workspace {}", pageId, workspaceId);
            } else {
                logger.info("Successfully added page {} to workspace {}", pageId, workspaceId);
            }
        } else if (parentPageId == null) {
            logger.warn("Root container page {} created without a workspace association.", pageId);
        }

        if (parentPageId != null && !parentPageId.isEmpty()) {
            updateParentChildRelationship(parentPageId, pageId);
        }

        return pageId;
    }

    public String createPage(PageComponent page) throws ExecutionException, InterruptedException {
        String pageId = pageRepository.createPage(page);

        String parentPageId = page.getParentPageId();
        if (parentPageId != null && !parentPageId.isEmpty()) {
            updateParentChildRelationship(parentPageId, pageId);
        }

        return pageId;
    }

    private void updateParentChildRelationship(String parentPageId, String childPageId)
            throws ExecutionException, InterruptedException {

        PageComponent parentPage = pageRepository.getPage(parentPageId);

        if (parentPage == null) {
            System.err.println("Warning: Parent page " + parentPageId + " not found when creating child " + childPageId);
            return;
        }

        if (parentPage.isLeaf()) {
            ContentPage contentParent = (ContentPage) parentPage;

            ContainerPage newContainerPage = new ContainerPage(
                    contentParent.getTitle(),
                    contentParent.getContent(),
                    contentParent.getParentPageId(),
                    contentParent.getOwner()
            );

            newContainerPage.setPageId(parentPageId);
            newContainerPage.setCreatedAt(parentPage.getCreatedAt());
            newContainerPage.setSharingInfo(parentPage.getSharingInfo());
            newContainerPage.setPublished(parentPage.isPublished());

            if (newContainerPage.getChildrenIds() == null) {
                newContainerPage.setChildrenIds(new ArrayList<>());
            }
            newContainerPage.getChildrenIds().add(childPageId);

            boolean updated = pageRepository.updatePage(newContainerPage);
            if (updated) {
                PageSubject subject = subjectManager.getSubject(parentPageId); // Now compiles
                subject.notifyObservers(newContainerPage);
            }
        } else if (parentPage instanceof ContainerPage) {
            ContainerPage containerParent = (ContainerPage) parentPage;

            if (containerParent.getChildrenIds() == null) {
                containerParent.setChildrenIds(new ArrayList<>());
            }

            if (!containerParent.getChildrenIds().contains(childPageId)) {
                containerParent.getChildrenIds().add(childPageId);
                containerParent.setLastUpdated(new Date());

                boolean updated = pageRepository.updatePage(containerParent);
                if (updated) {
                    PageSubject subject = subjectManager.getSubject(parentPageId); // Now compiles
                    subject.notifyObservers(containerParent);
                }
            }
        }
    }

    public PageComponent getPage(String pageId) throws ExecutionException, InterruptedException {
        PageComponent page = pageRepository.getPage(pageId);
        if (page == null) {
            return null;
        }

        String currentUserEmail = getCurrentUserEmail();
        if (!hasAccess(page, currentUserEmail, "view")) {
            logger.warn("Access Denied: User '{}' attempted to view page '{}' (Published: {}, Owner: {}) without permission.",
                    currentUserEmail != null ? currentUserEmail : "anonymous",
                    pageId, page.isPublished(), page.getOwner());
            throw new AccessDeniedException("User " + (currentUserEmail != null ? currentUserEmail : "anonymous") + " does not have view access to page " + pageId);
        }
        logger.debug("Access Granted: User '{}' viewing page '{}'.", currentUserEmail != null ? currentUserEmail : "anonymous", pageId);
        return page;
    }

    public List<PageComponent> getPagesByIds(List<String> pageIds) throws ExecutionException, InterruptedException {
        logger.debug("Fetching pages by IDs: {}", pageIds);
        List<PageComponent> pages = pageRepository.getPagesByIds(pageIds);
        logger.debug("Found {} pages for IDs: {}", pages.size(), pageIds);
        return pages;
    }

    public PageComponent getPageForEditing(String pageId) throws ExecutionException, InterruptedException {
        PageComponent page = pageRepository.getPage(pageId);
        if (page == null) {
            return null;
        }

        String currentUserEmail = getCurrentUserEmail();
        if (!hasAccess(page, currentUserEmail, "edit")) {
            logger.warn("Access Denied: User '{}' attempted to edit page '{}' (Owner: {}) without permission.",
                    currentUserEmail != null ? currentUserEmail : "anonymous",
                    pageId, page.getOwner());
            throw new AccessDeniedException("User " + (currentUserEmail != null ? currentUserEmail : "anonymous") + " does not have edit access to page " + pageId);
        }
        logger.debug("Edit Access Granted: User '{}' editing page '{}'.", currentUserEmail, pageId);
        return page;
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() != null &&
                !"anonymousUser".equals(authentication.getPrincipal().toString())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else {
                return authentication.getName();
            }
        }
        logger.trace("No authenticated user found, returning null for current user email.");
        return null;
    }

    private boolean hasAccess(PageComponent page, String userEmail, String requiredAccessLevel) throws ExecutionException, InterruptedException {
        if (page == null) {
            logger.trace("hasAccess check failed: Page is null.");
            return false;
        }

        if (userEmail != null && userEmail.equals(page.getOwner())) {
            logger.trace("hasAccess check passed for page '{}': User '{}' is owner.", page.getPageId(), userEmail);
            return true;
        }

        if (page.isPublished() && "view".equals(requiredAccessLevel)) {
            logger.trace("hasAccess check passed for page '{}': Page is published and required access is 'view'.", page.getPageId());
            return true;
        }

        if (userEmail != null && page.getSharingInfo() != null) {
            String grantedAccess = page.getSharingInfo().get(userEmail);
            if (grantedAccess != null) {
                if ("edit".equals(grantedAccess)) {
                    logger.trace("hasAccess check passed for page '{}': User '{}' has 'edit' access via sharing.", page.getPageId(), userEmail);
                    return true;
                } else if ("view".equals(grantedAccess) && "view".equals(requiredAccessLevel)) {
                    logger.trace("hasAccess check passed for page '{}': User '{}' has 'view' access via sharing.", page.getPageId(), userEmail);
                    return true;
                }
            }
        }

        if (page.getParentPageId() != null && !page.getParentPageId().isEmpty()) {
            logger.trace("Checking inherited access for page '{}' via parent '{}'.", page.getPageId(), page.getParentPageId());
            try {
                PageComponent parentPage = pageRepository.getPage(page.getParentPageId());
                if (parentPage != null) {
                    return hasAccess(parentPage, userEmail, requiredAccessLevel);
                } else {
                    logger.warn("Parent page '{}' not found during inherited access check for page '{}'.", page.getParentPageId(), page.getPageId());
                }
            } catch (Exception e) {
                logger.error("Error checking parent page access for page '{}': {}", page.getPageId(), e.getMessage());
                return false;
            }
        }

        logger.trace("hasAccess check failed for page '{}': No applicable access rules matched for user '{}' requiring '{}'.", page.getPageId(), userEmail != null ? userEmail : "anonymous", requiredAccessLevel);
        return false;
    }

    public List<PageComponent> getAllPages() throws ExecutionException, InterruptedException {
        return pageRepository.getAllPages();
    }

    public List<PageComponent> getChildPages(String parentPageId) throws ExecutionException, InterruptedException {
        PageComponent parent = getPage(parentPageId);
        if (parent == null || parent.isLeaf()) {
            return new ArrayList<>();
        }

        ContainerPage containerParent = (ContainerPage) parent;
        List<String> childIds = containerParent.getChildrenIds();
        List<PageComponent> children = new ArrayList<>();

        for (String childId : childIds) {
            PageComponent child = pageRepository.getPage(childId);
            if (child != null) {
                children.add(child);
            }
        }

        containerParent.setLoadedChildren(children);

        return children;
    }

    public boolean updatePage(String pageId, String newTitle, String newContent) throws ExecutionException, InterruptedException {
        PageComponent page = getPageForEditing(pageId);
        if (page == null) {
            logger.warn("Attempted to update non-existent or inaccessible page: {}", pageId);
            return false;
        }

        boolean changed = false;

        if (newTitle != null && !newTitle.equals(page.getTitle())) {
            page.setTitle(newTitle);
            changed = true;
            logger.debug("Updating title for page {}: '{}'", pageId, newTitle);
        }

        String currentContent = page.getContent();
        if (newContent != null && !newContent.equals(currentContent)) {
             if (page.isLeaf() && page instanceof ContentPage) {
                 ((ContentPage) page).setContent(newContent);
                 changed = true;
                 logger.debug("Updating content for ContentPage {}", pageId);
             } else if (!page.isLeaf() && page instanceof ContainerPage) {
                 ((ContainerPage) page).setSummary(newContent);
                 changed = true;
                 logger.debug("Updating summary for ContainerPage {}", pageId);
             } else {
                 logger.warn("Attempted to update content/summary on unexpected page type for page {}", pageId);
             }
        }

        if (changed) {
            page.setLastUpdated(new Date());
            boolean success = pageRepository.updatePage(page);
            if (success) {
                logger.info("Successfully updated page {}", pageId);
                PageSubject subject = subjectManager.getSubject(pageId); // Now compiles
                subject.notifyObservers(page);
            } else {
                logger.error("Repository failed to update page {}", pageId);
            }
            return success;
        } else {
            logger.info("No changes detected for page {}, skipping update.", pageId);
            return true;
        }
    }

    public List<String> deletePage(String pageId) throws ExecutionException, InterruptedException {
        PageComponent pageToDelete;
        try {
            pageToDelete = getPageForEditing(pageId);
        } catch (AccessDeniedException e) {
            logger.warn("Access denied for deleting page {}: {}", pageId, e.getMessage());
            throw e;
        } catch (Exception e) {
             logger.error("Error fetching page {} for deletion check: {}", pageId, e.getMessage());
             return Collections.emptyList();
        }

        if (pageToDelete == null) {
            logger.warn("Attempted to delete non-existent or inaccessible page: {}", pageId);
            return Collections.emptyList();
        }

        List<String> deletedIds = new ArrayList<>();
        return deletePageRecursive(pageToDelete, deletedIds);
    }

    private List<String> deletePageRecursive(PageComponent pageToDelete, List<String> deletedIds) throws ExecutionException, InterruptedException {
        String pageId = pageToDelete.getPageId();
        String parentId = pageToDelete.getParentPageId();

        if (!pageToDelete.isLeaf() && pageToDelete instanceof ContainerPage) {
            ContainerPage containerPage = (ContainerPage) pageToDelete;
            List<String> childIds = containerPage.getChildrenIds();
            if (childIds != null && !childIds.isEmpty()) {
                List<String> childIdsCopy = new ArrayList<>(childIds);
                for (String childId : childIdsCopy) {
                    try {
                        PageComponent childPage = pageRepository.getPage(childId);
                        if (childPage != null) {
                            deletePageRecursive(childPage, deletedIds);
                        } else {
                             logger.warn("Child page {} not found during recursive delete of parent {}.", childId, pageId);
                        }
                    } catch (AccessDeniedException e) {
                        logger.warn("Access denied while trying to recursively delete child page {}. Skipping deletion of this child.", childId);
                    } catch (Exception e) {
                        logger.error("Error recursively deleting child page {}: {}", childId, e.getMessage());
                    }
                }
            }
        }

        if (parentId != null && !parentId.isEmpty()) {
            try {
                PageComponent parentPage = pageRepository.getPage(parentId);
                if (parentPage != null && !parentPage.isLeaf() && parentPage instanceof ContainerPage) {
                    ContainerPage containerParent = (ContainerPage) parentPage;
                    boolean removed = containerParent.getChildrenIds().remove(pageId);
                    if (removed) {
                        pageRepository.updatePage(containerParent);
                        logger.debug("Removed child {} from parent {}", pageId, parentId);
                    }
                }
            } catch (Exception e) {
                logger.error("Error updating parent page {} after deleting child {}: {}", parentId, pageId, e.getMessage());
            }
        }

        boolean deleted = pageRepository.deletePage(pageId);

        if (deleted) {
            logger.info("Successfully deleted page: ID={}, Title='{}'", pageId, pageToDelete.getTitle());
            deletedIds.add(pageId);

            if (parentId != null && !parentId.isEmpty()) {
                String destination = "/topic/pages/" + parentId + "/children/deleted";
                logger.info("Sending WebSocket message to {}: {}", destination, pageId);
                messagingTemplate.convertAndSend(destination, pageId);
            }

            PageSubject subject = subjectManager.getSubject(pageId); // Now compiles
            subjectManager.removeSubjectIfUnused(pageId); // Clean up the subject manager

        } else {
             logger.error("Repository failed to delete page {}", pageId);
        }

        return deletedIds;
    }

    public boolean hasChildren(String pageId) throws ExecutionException, InterruptedException {
        List<PageComponent> children = getChildPages(pageId);
        return !children.isEmpty();
    }

    public boolean convertToContainerPage(String pageId) throws ExecutionException, InterruptedException {
        PageComponent page = getPage(pageId);

        if (page == null || !page.isLeaf()) {
            return false;
        }

        ContentPage contentPage = (ContentPage) page;

        ContainerPage containerPage = new ContainerPage(
                contentPage.getTitle(),
                contentPage.getContent(),
                contentPage.getParentPageId(),
                contentPage.getOwner()
        );
        containerPage.setPageId(pageId);

        return pageRepository.updatePage(containerPage);
    }

    public boolean sharePage(String pageId, String userEmailToShareWith, String accessLevel) throws ExecutionException, InterruptedException {
        if (!"view".equals(accessLevel) && !"edit".equals(accessLevel)) {
            throw new IllegalArgumentException("Invalid access level. Must be 'view' or 'edit'.");
        }

        PageComponent page = getPageForEditing(pageId);
        if (page == null) {
            return false;
        }

        page.addShare(userEmailToShareWith, accessLevel);
        page.setLastUpdated(new Date());
        boolean success = pageRepository.updatePage(page);
        if (success) {
            logger.info("Page {} shared with {} ({} access).", pageId, userEmailToShareWith, accessLevel);
            PageSubject subject = subjectManager.getSubject(pageId); // Now compiles
            subject.notifyObservers(page);
        }
        return success;
    }

    public boolean unsharePage(String pageId, String userEmailToUnshare) throws ExecutionException, InterruptedException {
        PageComponent page = getPageForEditing(pageId);
        if (page == null) {
            return false;
        }

        page.removeShare(userEmailToUnshare);
        page.setLastUpdated(new Date());
        boolean success = pageRepository.updatePage(page);
        if (success) {
            logger.info("Sharing removed for user {} from page {}.", userEmailToUnshare, pageId);
            PageSubject subject = subjectManager.getSubject(pageId); // Now compiles
            subject.notifyObservers(page);
        }
        return success;
    }

    public boolean publishPage(String pageId) throws ExecutionException, InterruptedException {
        PageComponent page = getPageForEditing(pageId);
        if (page == null) {
            return false;
        }

        page.setPublished(true);
        page.setLastUpdated(new Date());
        boolean success = pageRepository.updatePage(page);
        if (success) {
            logger.info("Page {} published successfully.", pageId);
            PageSubject subject = subjectManager.getSubject(pageId); // Now compiles
            subject.notifyObservers(page);
        }
        return success;
    }

    public boolean unpublishPage(String pageId) throws ExecutionException, InterruptedException {
        PageComponent page = getPageForEditing(pageId);
        if (page == null) {
            return false;
        }

        page.setPublished(false);
        page.setLastUpdated(new Date());
        boolean success = pageRepository.updatePage(page);
        if (success) {
            logger.info("Page {} unpublished successfully.", pageId);
            PageSubject subject = subjectManager.getSubject(pageId); // Now compiles
            subject.notifyObservers(page);
        }
        return success;
    }

    public PageComponent getExpandedPageWithSubpages(String pageId) throws ExecutionException, InterruptedException {
        PageComponent page = pageRepository.getPage(pageId);
        
        // Check permissions first
        String currentUserEmail = getCurrentUserEmail();
        if (!hasAccess(page, currentUserEmail, "view")) {
            throw new AccessDeniedException("User " + (currentUserEmail != null ? currentUserEmail : "anonymous") + 
                                           " does not have view access to page " + pageId);
        }
        
        // Handle different page types
        if (page instanceof ContainerPage) {
            ContainerPage containerPage = (ContainerPage) page;
            fetchSubPagesRecursively(containerPage);
            return containerPage;
        } else {
            return page;
        }
    }

    private void fetchSubPagesRecursively(ContainerPage page) throws ExecutionException, InterruptedException {
        List<PageComponent> children = page.getChildren(); // only valid for ContainerPage
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                PageComponent child = children.get(i);
                PageComponent fullChild = pageRepository.getPage(child.getPageId());

                if (fullChild instanceof ContainerPage) {
                    fetchSubPagesRecursively((ContainerPage) fullChild);
                }

                children.set(i, fullChild);
            }
            }}


    public List<PageComponent> getPagesOwnedByUser(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Service: Getting pages owned by user {}", userEmail);
        return pageRepository.findPagesByOwner(userEmail);
    }

    public List<PageComponent> getPagesSharedWithUser(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Service: Getting pages shared with user {}", userEmail);
        return pageRepository.findPagesSharedWithUser(userEmail);
    }

    /**
     * Retrieves pages that are shared directly with the specified user, excluding pages they own.
     *
     * @param userEmail The email of the user.
     * @return A list of PageComponent objects shared with the user.
     * @throws ExecutionException   If Firestore query execution fails.
     * @throws InterruptedException If the Firestore query thread is interrupted.
     */
    public List<PageComponent> getSharedPagesForUser(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Service: Getting pages shared directly with user {}", userEmail);
        // Now directly calls the repository method which handles the logic including excluding owned pages.
        return pageRepository.findPagesSharedWithUser(userEmail);
    }

    /**
     * Retrieves root pages (no parent) that are accessible by the specified user.
     * Accessible means the user owns the page OR it's shared with them (and they are not the owner).
     *
     * @param userEmail The email of the user.
     * @return A list of accessible root PageComponent objects.
     * @throws ExecutionException If there's an error during data retrieval.
     * @throws InterruptedException If the data retrieval is interrupted.
     */
    public List<PageComponent> getRootPagesForUser(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Service: Getting root pages accessible by user {}", userEmail);
        Set<PageComponent> accessiblePages = new HashSet<>();

        // Get pages owned by the user
        List<PageComponent> ownedPages = pageRepository.findPagesByOwner(userEmail); // Use repository method
        accessiblePages.addAll(ownedPages);
        logger.debug("Found {} pages owned by user {}", ownedPages.size(), userEmail);

        // Get pages shared with the user (excluding owned ones, handled by repository method)
        List<PageComponent> sharedPages = pageRepository.findPagesSharedWithUser(userEmail); // Use repository method
        accessiblePages.addAll(sharedPages);
        logger.debug("Found {} pages shared with user {}", sharedPages.size(), userEmail);

        // Filter for root pages (no parentId)
        List<PageComponent> rootPages = accessiblePages.stream()
                .filter(page -> page.getParentPageId() == null || page.getParentPageId().isEmpty())
                .collect(Collectors.toList());

        logger.info("Found {} unique root pages accessible by user {}", rootPages.size(), userEmail);
        return rootPages;
    }

    /**
     * Fetches all pages accessible by the user, including owned pages
     * and pages within workspaces they are a member of.
     * NOTE: Implementation details depend on data access strategy.
     *
     * @param userId The email/ID of the user.
     * @return A list of all accessible PageComponent objects.
     * @throws ExecutionException   If an error occurs during data fetching.
     * @throws InterruptedException If the data fetching thread is interrupted.
     */
    public List<PageComponent> getAllAccessiblePagesForUser(String userId) throws ExecutionException, InterruptedException {
        logger.debug("Service: Getting all accessible pages for user {}", userId);
        Set<PageComponent> accessiblePages = new HashSet<>();

        // 1. Get pages owned by the user
        List<PageComponent> ownedPages = pageRepository.findPagesByOwner(userId);
        accessiblePages.addAll(ownedPages);
        logger.debug("Found {} pages owned by user {}", ownedPages.size(), userId);

        // 2. Get workspaces the user is a member of
        List<Workspace> userWorkspaces = workspaceRepository.findWorkspacesByUserEmail(userId);
        List<String> userWorkspaceIds = userWorkspaces.stream()
                                                    .map(Workspace::getId)
                                                    .collect(Collectors.toList());
        logger.debug("User {} is a member of {} workspaces: {}", userId, userWorkspaceIds.size(), userWorkspaceIds);

        // 3. Get pages belonging to those workspaces (if any)
        if (!userWorkspaceIds.isEmpty()) {
            // Assuming PageRepository has a method like findPagesByWorkspaceIds
            // If not, this method needs to be added to PageRepository
            List<PageComponent> workspacePages = pageRepository.findPagesByWorkspaceIds(userWorkspaceIds);
            accessiblePages.addAll(workspacePages);
            logger.debug("Found {} pages belonging to user's workspaces", workspacePages.size());
        } else {
            logger.debug("User {} is not part of any workspaces, skipping workspace page fetch.", userId);
        }

        // 4. Combine and return (HashSet already handles duplicates)
        List<PageComponent> resultList = new ArrayList<>(accessiblePages);
        logger.info("Found {} total unique accessible pages for user {}", resultList.size(), userId);
        return resultList;
    }

    private void broadcastPageUpdate(String pageId, PageComponent page) {
        String destination = "/topic/pages/" + pageId;
        try {
            Map<String, Object> updatePayload = Map.of(
                "pageId", page.getPageId(),
                "title", page.getTitle(),
                "content", page.getContent(),
                "lastUpdated", page.getLastUpdated(),
                "isPublished", page.isPublished(),
                "sharingInfo", page.getSharingInfo()
            );
            logger.info("Broadcasting update for page {} to {}", pageId, destination);
            messagingTemplate.convertAndSend(destination, updatePayload);
        } catch (Exception e) {
            logger.error("Error broadcasting update for page {}: {}", pageId, e.getMessage(), e);

        }
    }
}
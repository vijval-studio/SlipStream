package com.example.SlipStream.controller;

import com.example.SlipStream.model.PageComponent;
import com.example.SlipStream.model.Workspace;
import com.example.SlipStream.service.PageService;
import com.example.SlipStream.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final WorkspaceService workspaceService;
    private final PageService pageService;

    public static class PageNode {
        private PageComponent page;
        private List<PageNode> children;

        public PageNode(PageComponent page) {
            this.page = page;
            this.children = new ArrayList<>();
        }

        public PageComponent getPage() {
            return page;
        }

        public List<PageNode> getChildren() {
            return children;
        }

        public void addChild(PageNode child) {
            this.children.add(child);
        }

        public static List<PageNode> buildTree(List<PageComponent> pages) {
            if (pages == null || pages.isEmpty()) {
                return new ArrayList<>();
            }

            List<PageNode> roots = new ArrayList<>();
            Map<String, PageNode> nodeMap = new HashMap<>();

            for (PageComponent page : pages) {
                if (page != null && page.getPageId() != null) {
                    nodeMap.put(page.getPageId(), new PageNode(page));
                } else {
                    logger.warn("Skipping null page or page with null ID during tree build (node map creation).");
                }
            }

            for (PageComponent page : pages) {
                if (page == null || page.getPageId() == null) continue;

                PageNode node = nodeMap.get(page.getPageId());
                if (node == null) continue;

                String parentId = page.getParentPageId();
                if (parentId != null && !parentId.isEmpty() && nodeMap.containsKey(parentId)) {
                    PageNode parentNode = nodeMap.get(parentId);
                    if (parentNode != null) {
                        parentNode.addChild(node);
                    } else {
                        logger.warn("Page {} has parentId {} which is not in the current tree's page list. Treating as root.", page.getPageId(), parentId);
                        roots.add(node);
                    }
                } else {
                    roots.add(node);
                }
            }

            roots.sort(Comparator.comparing(n -> n.getPage().getTitle() != null ? n.getPage().getTitle() : "", String.CASE_INSENSITIVE_ORDER));
            sortChildrenRecursive(roots);

            logger.debug("Built tree with {} root nodes.", roots.size());
            return roots;
        }

        private static void sortChildrenRecursive(List<PageNode> nodes) {
            for (PageNode node : nodes) {
                if (!node.getChildren().isEmpty()) {
                    node.getChildren().sort(Comparator.comparing(n -> n.getPage().getTitle() != null ? n.getPage().getTitle() : "", String.CASE_INSENSITIVE_ORDER));
                    sortChildrenRecursive(node.getChildren());
                }
            }
        }
    }

    @Autowired
    public DashboardController(WorkspaceService workspaceService, PageService pageService) {
        this.workspaceService = workspaceService;
        this.pageService = pageService;
    }

    @GetMapping
    public String showDashboard(Model model, RedirectAttributes redirectAttributes) {
        String currentUserEmail = getCurrentUserEmail();
        if (currentUserEmail == null) {
            logger.warn("User not authenticated, redirecting to login.");
            redirectAttributes.addFlashAttribute("login_error", "Please log in to view the dashboard.");
            return "redirect:/login";
        }

        model.addAttribute("currentUserEmail", currentUserEmail);
        model.addAttribute("workspaces", Collections.emptyList());
        model.addAttribute("workspacePageTrees", Collections.emptyMap());
        model.addAttribute("independentPageNodes", Collections.emptyList());
        model.addAttribute("sharedPages", Collections.emptyList());

        try {
            List<Workspace> workspaces = workspaceService.getWorkspacesForUser(currentUserEmail);
            model.addAttribute("workspaces", workspaces);
            logger.info("Fetched {} workspaces for user {}", workspaces.size(), currentUserEmail);

            logger.debug("Attempting to fetch all accessible pages for user {}", currentUserEmail);
            List<PageComponent> allAccessiblePages = pageService.getAllAccessiblePagesForUser(currentUserEmail);
            logger.info("Fetched {} total accessible pages for user {}", allAccessiblePages.size(), currentUserEmail);

            Map<String, PageComponent> allPagesMap = allAccessiblePages.stream()
                .filter(p -> p != null && p.getPageId() != null)
                .collect(Collectors.toMap(PageComponent::getPageId, p -> p, (p1, p2) -> p1));

            Map<String, String> rootPageToWorkspaceIdMap = new HashMap<>();
            for (Workspace ws : workspaces) {
                if (ws.getRootPageIds() != null) {
                    for (String rootPageId : ws.getRootPageIds()) {
                        if (rootPageId != null && allPagesMap.containsKey(rootPageId)) {
                            rootPageToWorkspaceIdMap.put(rootPageId, ws.getId());
                            logger.trace("Mapped root page {} to workspace {}", rootPageId, ws.getId());
                        } else {
                            logger.warn("Workspace {} lists root page ID {} which is null, not accessible, or not found.", ws.getId(), rootPageId);
                        }
                    }
                }
            }
            logger.debug("Created root page to workspace ID map with {} entries.", rootPageToWorkspaceIdMap.size());

            Map<String, List<PageComponent>> pagesByWorkspaceId = new HashMap<>();
            List<PageComponent> independentPages = new ArrayList<>();
            Set<String> assignedPageIds = new HashSet<>();

            for (PageComponent page : allAccessiblePages) {
                if (page == null || page.getPageId() == null) continue;

                String pageId = page.getPageId();
                String effectiveWorkspaceId = findWorkspaceIdForPage(page, allPagesMap, rootPageToWorkspaceIdMap);

                if (effectiveWorkspaceId != null) {
                    pagesByWorkspaceId.computeIfAbsent(effectiveWorkspaceId, k -> new ArrayList<>()).add(page);
                    assignedPageIds.add(pageId);
                    logger.trace("Categorized page {} (and potential children) into workspace {}", pageId, effectiveWorkspaceId);
                } else if (page.getOwner() != null && page.getOwner().equals(currentUserEmail) && !assignedPageIds.contains(pageId)) {
                    independentPages.add(page);
                    logger.trace("Categorized page {} as potentially independent (owned by user)", pageId);
                } else {
                    logger.trace("Page {} is neither in a workspace nor independently owned by the user (might be shared or child of shared)", pageId);
                }
            }

            logger.info("Categorized pages: {} workspaces with pages, {} potentially independent pages.",
                    pagesByWorkspaceId.size(), independentPages.size());
            logger.debug("Workspace IDs with pages found: {}", pagesByWorkspaceId.keySet());
            pagesByWorkspaceId.forEach((wsId, pages) -> logger.debug("Workspace {}: {} pages", wsId, pages.size()));
            logger.debug("Independent pages count (before tree building): {}", independentPages.size());

            Map<String, List<PageNode>> workspacePageTrees = new HashMap<>();
            for (Map.Entry<String, List<PageComponent>> entry : pagesByWorkspaceId.entrySet()) {
                String workspaceId = entry.getKey();
                List<PageComponent> workspacePages = entry.getValue();
                if (!workspacePages.isEmpty()) {
                    logger.debug("Building tree for workspace {} using {} pages.", workspaceId, workspacePages.size());
                    List<PageNode> pageNodes = PageNode.buildTree(workspacePages);
                    workspacePageTrees.put(workspaceId, pageNodes);
                    logger.info("Built page tree for workspace {} with {} root nodes.", workspaceId, pageNodes.size());
                }
            }
            model.addAttribute("workspacePageTrees", workspacePageTrees);

            logger.debug("Building tree for independent pages using {} pages.", independentPages.size());
            List<PageNode> independentPageNodes = PageNode.buildTree(independentPages);
            model.addAttribute("independentPageNodes", independentPageNodes);
            logger.info("Built independent page tree with {} root nodes.", independentPageNodes.size());

            logger.debug("Adding to model: workspacePageTrees size = {}, independentPageNodes size = {}",
                    workspacePageTrees.size(), independentPageNodes.size());

            // Fetch shared pages
            List<PageComponent> sharedPages = pageService.getSharedPagesForUser(currentUserEmail);
            logger.info("Fetched {} shared pages for user {}", sharedPages.size(), currentUserEmail);

            // Sort the fetched shared pages by title
            sharedPages.sort(Comparator.comparing(p -> p.getTitle() != null ? p.getTitle() : "", String.CASE_INSENSITIVE_ORDER));

            // Add the unfiltered (but sorted) list to the model
            model.addAttribute("sharedPages", sharedPages);
            logger.info("Displaying {} shared pages for user {}", sharedPages.size(), currentUserEmail);

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error fetching dashboard data for user {}: {}", currentUserEmail, e.getMessage(), e);
            Thread.currentThread().interrupt();
            model.addAttribute("errorMessage", "Could not load dashboard data. Please try again later.");
            model.addAttribute("workspaces", Collections.emptyList());
            model.addAttribute("workspacePageTrees", Collections.emptyMap());
            model.addAttribute("independentPageNodes", Collections.emptyList());
            model.addAttribute("sharedPages", Collections.emptyList());
        } catch (Exception e) {
            logger.error("Unexpected error fetching dashboard data for user {}: {}", currentUserEmail, e.getMessage(), e);
            model.addAttribute("errorMessage", "An unexpected error occurred.");
            model.addAttribute("workspaces", Collections.emptyList());
            model.addAttribute("workspacePageTrees", Collections.emptyMap());
            model.addAttribute("independentPageNodes", Collections.emptyList());
            model.addAttribute("sharedPages", Collections.emptyList());
        }

        return "dashboard";
    }

    private String findWorkspaceIdForPage(PageComponent page, Map<String, PageComponent> allPagesMap, Map<String, String> rootPageToWorkspaceIdMap) {
        if (page == null || page.getPageId() == null) {
            return null;
        }

        Set<String> visited = new HashSet<>();
        PageComponent current = page;
        String pageId = page.getPageId();

        logger.trace("Finding workspace for page: {}", pageId);

        while (current != null && !visited.contains(current.getPageId())) {
            String currentId = current.getPageId();
            visited.add(currentId);
            logger.trace("Checking page {} in ancestry path", currentId);

            if (rootPageToWorkspaceIdMap.containsKey(currentId)) {
                String workspaceId = rootPageToWorkspaceIdMap.get(currentId);
                logger.trace("Page {} is a root page of workspace {}. Assigning.", currentId, workspaceId);
                return workspaceId;
            }

            String parentId = current.getParentPageId();
            if (parentId != null && !parentId.isEmpty()) {
                current = allPagesMap.get(parentId);
                if (current == null) {
                    logger.trace("Parent page {} not found or not accessible. Stopping ancestry search.", parentId);
                    break;
                }
            } else {
                logger.trace("Page {} has no parent. Stopping ancestry search.", currentId);
                break;
            }
        }

        if (current != null && visited.contains(current.getPageId())) {
            logger.warn("Cycle detected in parent references starting from page {}. Stopping ancestry search.", page.getPageId());
        }

        logger.trace("Page {} does not belong to any workspace based on root page ancestry.", page.getPageId());
        return null;
    }

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
}

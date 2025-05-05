package com.example.SlipStream.repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.SlipStream.model.ContainerPage;
import com.example.SlipStream.model.ContentPage;
import com.example.SlipStream.model.PageComponent;
import com.google.cloud.firestore.Query;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.FieldValue;

@Repository
public class FirebasePageRepository implements PageRepository {

    private static final String COLLECTION_NAME = "Pages";
    private static final Logger logger = LoggerFactory.getLogger(FirebasePageRepository.class);

    @Override
    public String createPage(PageComponent page) throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        
        if (page.getPageId() == null || page.getPageId().isEmpty()) {
            page.setPageId("page_" + UUID.randomUUID().toString());
        }
        
        if (page.getCreatedAt() == null) {
            page.setCreatedAt(new Date());
        }
        page.setLastUpdated(new Date());
        
        Map<String, Object> pageMap = convertToMap(page);
        
        firestore.collection(COLLECTION_NAME).document(page.getPageId()).set(pageMap).get();
        return page.getPageId();
    }

    @Override
    public PageComponent getPage(String pageId) throws ExecutionException, InterruptedException {
        System.out.println("Looking for page with pageId: " + pageId);
        
        Firestore firestore = FirestoreClient.getFirestore();
        
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(pageId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (document.exists()) {
            return convertToPageComponent(document);
        }
        
        Query query = firestore.collection(COLLECTION_NAME).whereEqualTo("pageId", pageId);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        System.out.println("Query complete. Found " + documents.size() + " matching documents");
        
        if (!documents.isEmpty()) {
            return convertToPageComponent(documents.get(0));
        }
        
        System.out.println("No document found with pageId: " + pageId);
        return null;
    }

    @Override
    public List<PageComponent> getAllPages() throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        List<PageComponent> pages = new ArrayList<>();
        
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        for (QueryDocumentSnapshot document : documents) {
            pages.add(convertToPageComponent(document));
        }
        
        return pages;
    }

    @Override
    public List<PageComponent> getChildPages(String parentPageId) throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        List<PageComponent> childPages = new ArrayList<>();
        
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
            .whereEqualTo("parentPageId", parentPageId)
            .get();
            
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        for (QueryDocumentSnapshot document : documents) {
            childPages.add(convertToPageComponent(document));
        }
        
        return childPages;
    }

    @Override
    public boolean updatePageContent(String pageId, String newContent) throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(pageId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (document.exists()) {
            docRef.update("content", newContent, "lastUpdated", new Date()).get();
            return true;
        }
        
        Query query = firestore.collection(COLLECTION_NAME).whereEqualTo("pageId", pageId);
        QuerySnapshot querySnapshot = query.get().get();
        
        if (querySnapshot.isEmpty()) {
            return false;
        }
        
        docRef = querySnapshot.getDocuments().get(0).getReference();
        
        docRef.update("content", newContent, "lastUpdated", new Date()).get();
        return true;
    }

    @Override
    public boolean updatePage(PageComponent page) throws ExecutionException, InterruptedException {
        if (page == null || page.getPageId() == null || page.getPageId().isEmpty()) {
            return false;
        }
        
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(page.getPageId());
        
        DocumentSnapshot document = docRef.get().get();
        if (!document.exists()) {
            return false;
        }
        
        Map<String, Object> pageMap = convertToMap(page);
        ApiFuture<WriteResult> result = docRef.set(pageMap);
        result.get();
        
        return true;
    }

    @Override
    public boolean deletePage(String pageId) throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        
        PageComponent page = getPage(pageId);
        if (page == null) {
            return false;
        }
        
        if (page.getParentPageId() != null && !page.getParentPageId().isEmpty()) {
            PageComponent parentPage = getPage(page.getParentPageId());
            if (parentPage instanceof ContainerPage) {
                ContainerPage container = (ContainerPage) parentPage;
                List<String> childrenIds = container.getChildrenIds();
                if (childrenIds != null) {
                    childrenIds.remove(pageId);
                    updatePage(container);
                }
            }
        }
        
        if (page instanceof ContainerPage) {
            ContainerPage container = (ContainerPage) page;
            List<String> childrenIds = container.getChildrenIds();
            if (childrenIds != null) {
                for (String childId : new ArrayList<>(childrenIds)) {
                    deletePage(childId);
                }
            }
        }
        
        firestore.collection(COLLECTION_NAME).document(pageId).delete().get();
        return true;
    }
    
    @Override
    public List<PageComponent> getPagesByIds(List<String> pageIds) throws ExecutionException, InterruptedException {
        if (pageIds == null || pageIds.isEmpty()) {
            return new ArrayList<>();
        }
        Firestore firestore = FirestoreClient.getFirestore();
        List<PageComponent> pages = new ArrayList<>();
        for (String pageId : pageIds) {
            try {
                PageComponent page = getPage(pageId);
                if (page != null) {
                    pages.add(page);
                } else {
                    logger.warn("Page with ID {} not found while fetching by IDs.", pageId);
                }
            } catch (Exception e) {
                 logger.error("Error fetching page with ID {} by IDs: {}", pageId, e.getMessage(), e);
            }
        }
        return pages;
    }

    @Override
    public List<PageComponent> findPagesByOwner(String ownerEmail) throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        List<PageComponent> pages = new ArrayList<>();
        Query query = firestore.collection(COLLECTION_NAME).whereEqualTo("owner", ownerEmail);
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (QueryDocumentSnapshot document : documents) {
            pages.add(convertToPageComponent(document));
        }
        return pages;
    }

    @Override
    public List<PageComponent> findPagesSharedWithUser(String userEmail) throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        List<PageComponent> pages = new ArrayList<>();
        logger.debug("Repository: Finding pages where sharingInfo map contains key '{}'", userEmail);

        FieldPath fieldPath = FieldPath.of("sharingInfo", userEmail);

        Query query = firestore.collection(COLLECTION_NAME).whereNotEqualTo(fieldPath, null);

        ApiFuture<QuerySnapshot> future = query.get();
        try {
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            logger.debug("Repository: Query for shared pages returned {} documents.", documents.size());
            for (QueryDocumentSnapshot document : documents) {
                PageComponent page = convertToPageComponent(document);
                if (page != null && page.getSharingInfo() != null && page.getSharingInfo().containsKey(userEmail)) {
                     if (!userEmail.equals(page.getOwner())) {
                        pages.add(page);
                        logger.trace("Repository: Added shared page {} to results for user {}", page.getPageId(), userEmail);
                     } else {
                        logger.trace("Repository: Skipping page {} because user {} is the owner.", page.getPageId(), userEmail);
                     }
                } else {
                    logger.warn("Repository: Document {} matched shared query but validation failed.", document.getId());
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Repository: Error executing query for pages shared with user {}: {}", userEmail, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
             logger.error("Repository: Unexpected error querying pages shared with user {}: {}", userEmail, e.getMessage(), e);
             throw new RuntimeException("Failed to query shared pages due to repository error.", e);
        }
        logger.info("Repository: Found {} pages shared with user {} (excluding owned).", pages.size(), userEmail);
        return pages;
    }

    @Override
    public boolean sharePageWithUser(String pageId, String userEmail, String accessLevel) throws ExecutionException, InterruptedException {
        if (!"view".equals(accessLevel) && !"edit".equals(accessLevel)) {
            logger.error("Invalid access level '{}' provided for sharing page {}", accessLevel, pageId);
            return false;
        }
        if (userEmail == null || userEmail.isEmpty()) {
             logger.error("User email cannot be null or empty for sharing page {}", pageId);
             return false;
        }

        Firestore firestore = FirestoreClient.getFirestore();
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(pageId);

        // Use dot notation for nested fields in the map key
        String fieldPathString = "sharingInfo." + userEmail;

        Map<String, Object> updates = new HashMap<>();
        updates.put(fieldPathString, accessLevel); // Use the string path as the key
        updates.put("lastUpdated", new Date());

        logger.info("Updating sharing for page {}: Setting {} = {}", pageId, fieldPathString, accessLevel);
        ApiFuture<WriteResult> future = docRef.update(updates);

        try {
            future.get();
            logger.info("Successfully updated sharing for page {}", pageId);
            return true;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to update sharing for page {}: {}", pageId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
             logger.error("Unexpected error updating sharing for page {}: {}", pageId, e.getMessage(), e);
             return false;
        }
    }

    public boolean unsharePageWithUser(String pageId, String userEmail) throws ExecutionException, InterruptedException {
         if (userEmail == null || userEmail.isEmpty()) {
             logger.error("User email cannot be null or empty for unsharing page {}", pageId);
             return false;
         }

        Firestore firestore = FirestoreClient.getFirestore();
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(pageId);

        // Use dot notation for nested fields in the map key
        String fieldPathString = "sharingInfo." + userEmail;

        Map<String, Object> updates = new HashMap<>();
        updates.put(fieldPathString, FieldValue.delete()); // Use the string path as the key
        updates.put("lastUpdated", new Date());

        logger.info("Attempting to remove user {} from sharingInfo map for page {}", userEmail, pageId);
        ApiFuture<WriteResult> future = docRef.update(updates);

        try {
            future.get();
            logger.info("Successfully updated sharing (removed user) for page {}", pageId);
            return true;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to update sharing (remove user) for page {}: {}", pageId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
             logger.error("Unexpected error updating sharing (remove user) for page {}: {}", pageId, e.getMessage(), e);
             return false;
        }
    }

    @Override
    public List<PageComponent> findPagesByWorkspaceIds(List<String> workspaceIds) throws ExecutionException, InterruptedException {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        Firestore firestore = FirestoreClient.getFirestore();
        List<PageComponent> pages = new ArrayList<>();
        
        Query query = firestore.collection(COLLECTION_NAME).whereIn("workspaceId", workspaceIds);
        ApiFuture<QuerySnapshot> future = query.get();
        
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (QueryDocumentSnapshot document : documents) {
            pages.add(convertToPageComponent(document));
        }
        
        return pages;
    }
    
    private Map<String, Object> convertToMap(PageComponent page) {
        Map<String, Object> map = new HashMap<>();
        map.put("pageId", page.getPageId());
        map.put("title", page.getTitle());
        map.put("parentPageId", page.getParentPageId());
        map.put("owner", page.getOwner());
        map.put("createdAt", page.getCreatedAt());
        map.put("lastUpdated", page.getLastUpdated());
        map.put("isLeaf", page.isLeaf());
        map.put("isPublished", page.isPublished());
        map.put("sharingInfo", page.getSharingInfo() != null ? page.getSharingInfo() : new HashMap<>());

        map.put("content", page.getContent());
        
        if (!page.isLeaf() && page instanceof ContainerPage) {
            List<String> childrenIds = ((ContainerPage) page).getChildrenIds();
            map.put("childrenIds", childrenIds != null ? childrenIds : new ArrayList<String>());
        } else {
            map.put("childrenIds", new ArrayList<String>());
        }
        
        return map;
    }
    
    private PageComponent convertToPageComponent(DocumentSnapshot document) {
        Map<String, Object> data = document.getData();
        if (data == null) return null;
        
        Boolean isLeaf = (Boolean) data.get("isLeaf");
        PageComponent component;
        
        if (isLeaf != null && isLeaf) {
            component = new ContentPage();
            String content = (String) data.get("content");
            ((ContentPage) component).setContent(content);
        } else {
            component = new ContainerPage();
            String summary = (String) data.get("content");
            ((ContainerPage) component).setSummary(summary);
            
            List<String> childrenIds = (List<String>) data.get("childrenIds");
            if (childrenIds != null) {
                ((ContainerPage) component).setChildrenIds(childrenIds);
            }
        }
        
        component.setPageId((String) data.get("pageId"));
        component.setTitle((String) data.get("title"));
        component.setOwner((String) data.get("owner"));
        component.setParentPageId((String) data.get("parentPageId"));
        component.setPublished(data.get("isPublished") != null ? (Boolean) data.get("isPublished") : false);
        component.setWorkspaceId((String) data.get("workspaceId"));

        Map<String, String> sharingInfo = (Map<String, String>) data.get("sharingInfo");
        component.setSharingInfo(sharingInfo != null ? sharingInfo : new HashMap<>());
        
        if (data.get("createdAt") instanceof com.google.cloud.Timestamp) {
            component.setCreatedAt(((com.google.cloud.Timestamp) data.get("createdAt")).toDate());
        }
        if (data.get("lastUpdated") instanceof com.google.cloud.Timestamp) {
            component.setLastUpdated(((com.google.cloud.Timestamp) data.get("lastUpdated")).toDate());
        }
        
        return component;
    }
}
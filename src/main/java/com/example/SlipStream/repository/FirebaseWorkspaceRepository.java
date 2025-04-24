package com.example.SlipStream.repository;

import com.example.SlipStream.model.Workspace;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class FirebaseWorkspaceRepository implements WorkspaceRepository {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseWorkspaceRepository.class);
    private static final String COLLECTION_NAME = "Workspaces";

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    @Override
    public String createWorkspace(Workspace workspace) throws ExecutionException, InterruptedException {
        if (workspace.getId() == null || workspace.getId().isEmpty()) {
            throw new IllegalArgumentException("Workspace ID must be set before creation.");
        }
        if (workspace.getCreatedAt() == null) {
            workspace.setCreatedAt(new Date());
        }
        workspace.setLastUpdated(new Date());

        ApiFuture<WriteResult> future = getFirestore().collection(COLLECTION_NAME)
                .document(workspace.getId())
                .set(workspace);
        future.get(); // Wait for completion
        logger.info("Workspace created with ID: {}", workspace.getId());
        return workspace.getId();
    }

    @Override
    public Workspace getWorkspaceById(String workspaceId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = getFirestore().collection(COLLECTION_NAME).document(workspaceId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return convertToWorkspace(document); // Use conversion method
        } else {
            logger.warn("Workspace not found with ID: {}", workspaceId);
            return null;
        }
    }

    @Override
    public List<Workspace> getWorkspacesByOwner(String ownerId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = getFirestore().collection(COLLECTION_NAME)
                .whereEqualTo("owner", ownerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
        return processQuerySnapshot(future);
    }

    @Override
    public List<Workspace> getWorkspacesByMember(String memberId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = getFirestore().collection(COLLECTION_NAME)
                .whereArrayContains("members", memberId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
        return processQuerySnapshot(future);
    }

    @Override
    public List<Workspace> getWorkspacesForUser(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Fetching workspaces where user '{}' is a member.", userEmail);
        ApiFuture<QuerySnapshot> future = getFirestore().collection(COLLECTION_NAME)
                .whereArrayContains("members", userEmail)
                .get();
        List<Workspace> workspaces = processQuerySnapshot(future);
        logger.debug("Found {} workspaces for user '{}'.", workspaces.size(), userEmail);
        return workspaces;
    }

    @Override
    public boolean updateWorkspace(Workspace workspace) throws ExecutionException, InterruptedException {
        if (workspace.getId() == null || workspace.getId().isEmpty()) {
            throw new IllegalArgumentException("Workspace ID must be set for update.");
        }
        workspace.setLastUpdated(new Date());
        ApiFuture<WriteResult> future = getFirestore().collection(COLLECTION_NAME)
                .document(workspace.getId())
                .set(workspace, SetOptions.merge());
        future.get();
        logger.info("Workspace updated with ID: {}", workspace.getId());
        return true;
    }

    @Override
    public boolean deleteWorkspace(String workspaceId) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> future = getFirestore().collection(COLLECTION_NAME)
                .document(workspaceId)
                .delete();
        future.get();
        logger.info("Workspace deleted with ID: {}", workspaceId);
        return true;
    }

    @Override
    public boolean addMemberToWorkspace(String workspaceId, String memberId) throws ExecutionException, InterruptedException {
        DocumentReference workspaceRef = getFirestore().collection(COLLECTION_NAME).document(workspaceId);
        ApiFuture<WriteResult> future = workspaceRef.update("members", FieldValue.arrayUnion(memberId), "lastUpdated", new Date());
        future.get();
        logger.info("Member {} added to workspace {}", memberId, workspaceId);
        return true;
    }

    @Override
    public boolean removeMemberFromWorkspace(String workspaceId, String memberId) throws ExecutionException, InterruptedException {
        Workspace ws = getWorkspaceById(workspaceId);
        if (ws != null && ws.getOwner().equals(memberId)) {
            logger.warn("Attempted to remove owner {} from workspace {}", memberId, workspaceId);
            return false;
        }

        DocumentReference workspaceRef = getFirestore().collection(COLLECTION_NAME).document(workspaceId);
        ApiFuture<WriteResult> future = workspaceRef.update("members", FieldValue.arrayRemove(memberId), "lastUpdated", new Date());
        future.get();
        logger.info("Member {} removed from workspace {}", memberId, workspaceId);
        return true;
    }

    @Override
    public boolean addRootPageToWorkspace(String workspaceId, String pageId) throws ExecutionException, InterruptedException {
        DocumentReference workspaceRef = getFirestore().collection(COLLECTION_NAME).document(workspaceId);
        ApiFuture<WriteResult> future = workspaceRef.update("rootPageIds", FieldValue.arrayUnion(pageId), "lastUpdated", new Date());
        future.get();
        logger.info("Root page {} added to workspace {}", pageId, workspaceId);
        return true;
    }

    @Override
    public boolean removeRootPageFromWorkspace(String workspaceId, String pageId) throws ExecutionException, InterruptedException {
        DocumentReference workspaceRef = getFirestore().collection(COLLECTION_NAME).document(workspaceId);
        ApiFuture<WriteResult> future = workspaceRef.update("rootPageIds", FieldValue.arrayRemove(pageId), "lastUpdated", new Date());
        future.get();
        logger.info("Root page {} removed from workspace {}", pageId, workspaceId);
        return true;
    }

    private Workspace convertToWorkspace(DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return null;
        }
        Workspace workspace = document.toObject(Workspace.class);
        if (workspace != null) {
            workspace.setId(document.getId());
        }
        return workspace;
    }

    private List<Workspace> processQuerySnapshot(ApiFuture<QuerySnapshot> future) throws ExecutionException, InterruptedException {
        List<Workspace> workspaces = new ArrayList<>();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (QueryDocumentSnapshot document : documents) {
            workspaces.add(convertToWorkspace(document));
        }
        return workspaces;
    }

    @Override
    public List<Workspace> findWorkspacesByUserEmail(String userEmail) throws ExecutionException, InterruptedException {
        logger.debug("Repository: Finding workspaces for user email: {}", userEmail);
        ApiFuture<QuerySnapshot> future = getFirestore().collection(COLLECTION_NAME)
                .whereArrayContains("members", userEmail)
                .get();
        List<Workspace> workspaces = processQuerySnapshot(future);
        logger.info("Repository: Found {} workspaces for user {}", workspaces.size(), userEmail);
        return workspaces;
    }
}

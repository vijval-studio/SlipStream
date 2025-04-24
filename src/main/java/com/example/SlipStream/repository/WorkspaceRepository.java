package com.example.SlipStream.repository;

import com.example.SlipStream.model.Workspace;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface WorkspaceRepository {

    String createWorkspace(Workspace workspace) throws ExecutionException, InterruptedException; // Changed parameter type

    Workspace getWorkspaceById(String workspaceId) throws ExecutionException, InterruptedException;

    List<Workspace> getWorkspacesByOwner(String ownerId) throws ExecutionException, InterruptedException;

    List<Workspace> getWorkspacesByMember(String memberId) throws ExecutionException, InterruptedException;

    List<Workspace> getWorkspacesForUser(String userEmail) throws ExecutionException, InterruptedException;

    boolean updateWorkspace(Workspace workspace) throws ExecutionException, InterruptedException;

    boolean deleteWorkspace(String workspaceId) throws ExecutionException, InterruptedException;

    boolean addMemberToWorkspace(String workspaceId, String memberId) throws ExecutionException, InterruptedException;

    boolean removeMemberFromWorkspace(String workspaceId, String memberId) throws ExecutionException, InterruptedException;

    boolean addRootPageToWorkspace(String workspaceId, String pageId) throws ExecutionException, InterruptedException;

    boolean removeRootPageFromWorkspace(String workspaceId, String pageId) throws ExecutionException, InterruptedException;

    List<Workspace> findWorkspacesByUserEmail(String userEmail) throws ExecutionException, InterruptedException; // New method
}

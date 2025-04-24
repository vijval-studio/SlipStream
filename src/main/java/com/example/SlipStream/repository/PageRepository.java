package com.example.SlipStream.repository;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.example.SlipStream.model.PageComponent;

public interface PageRepository {
    String createPage(PageComponent page) throws ExecutionException, InterruptedException;
    PageComponent getPage(String pageId) throws ExecutionException, InterruptedException;
    List<PageComponent> getAllPages() throws ExecutionException, InterruptedException;
    List<PageComponent> getChildPages(String parentPageId) throws ExecutionException, InterruptedException;
    boolean updatePageContent(String pageId, String newContent) throws ExecutionException, InterruptedException;
    boolean deletePage(String pageId) throws ExecutionException, InterruptedException;
    boolean updatePage(PageComponent page) throws ExecutionException, InterruptedException;
    List<PageComponent> getPagesByIds(List<String> pageIds) throws ExecutionException, InterruptedException;
    List<PageComponent> findPagesByOwner(String ownerEmail) throws ExecutionException, InterruptedException;
    List<PageComponent> findPagesSharedWithUser(String userEmail) throws ExecutionException, InterruptedException;
    boolean sharePageWithUser(String pageId, String userEmail, String accessLevel) throws ExecutionException, InterruptedException;
    List<PageComponent> findPagesByWorkspaceIds(List<String> workspaceIds) throws ExecutionException, InterruptedException;
}
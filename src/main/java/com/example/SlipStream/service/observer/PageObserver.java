package com.example.SlipStream.service.observer;

import com.example.SlipStream.model.PageComponent;

/**
 * Interface for observers interested in PageComponent updates.
 */
public interface PageObserver {
    /**
     * Called when the observed PageComponent is updated.
     * @param page The updated PageComponent.
     */
    void update(PageComponent page);
}

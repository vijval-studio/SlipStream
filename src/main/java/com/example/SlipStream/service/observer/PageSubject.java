package com.example.SlipStream.service.observer;

import com.example.SlipStream.model.PageComponent;

/**
 * Interface for the subject (PageComponent) in the Observer pattern.
 */
public interface PageSubject {
    /**
     * Attaches an observer to the subject.
     * @param observer The observer to attach.
     */
    void attach(PageObserver observer);

    /**
     * Detaches an observer from the subject.
     * @param observer The observer to detach.
     */
    void detach(PageObserver observer);

    /**
     * Notifies all attached observers about a change.
     * @param page The updated PageComponent state to notify observers with.
     */
    void notifyObservers(PageComponent page);
}

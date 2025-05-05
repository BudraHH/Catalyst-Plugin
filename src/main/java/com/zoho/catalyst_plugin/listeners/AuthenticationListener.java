package com.zoho.catalyst_plugin.listeners; // Adjust package if needed

import com.intellij.util.messages.Topic;
import java.util.EventListener;

/**
 * Listener interface for authentication state change events within the application.
 * Components interested in auth changes (like the Tool Window panel) should implement
 * or subscribe to this listener via the MessageBus.
 */
public interface AuthenticationListener extends EventListener {

    /**
     * Defines the communication channel (Topic) for authentication changes.
     * We use the application-level MessageBus for broad communication.
     * The display name should be unique but is mostly for debugging/inspection.
     */
    // <<< Corrected Factory Method >>>
    Topic<AuthenticationListener> TOPIC = Topic.create( // <<< Use Topic.create()
            "Catalyst LSK Auth Change", // Display name for the topic
            AuthenticationListener.class    // The listener interface class this topic uses
            // Optional third argument for BroadcastDirection, defaults to TO_CHILDREN which is usually fine for app-level
            // Topic.BroadcastDirection.NONE // Use this if you ONLY want direct subscribers on the App bus to receive it
    );

    /**
     * Method called by the publisher when the authentication state has potentially changed
     * (e.g., after a successful sign-in or sign-out). Subscribers implement this
     * method to react accordingly (e.g., refresh their UI).
     */
    void authenticationStateChanged();
}
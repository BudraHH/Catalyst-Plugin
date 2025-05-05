package com.zoho.catalyst_plugin.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import com.zoho.catalyst_plugin.service.AuthService;
import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.util.AuthHelper;

/**
 * Action triggered from the Tools menu to manually initiate the GitHub sign-in process.
 * Uses AuthHelper to perform the core sign-in flow logic.
 */
public class SignInAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(SignInAction.class);

    /**
     * Enable the action only if the user is not already signed in.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean isSignedIn = AuthService.getInstance().isSignedIn();
        e.getPresentation().setEnabledAndVisible(project != null && !isSignedIn);
        e.getPresentation().setText(isSignedIn ? "Catalyst LSK: Already Signed In" : "Catalyst LSK: Sign In with GitHub...");
    }

    /**
     * Executes the sign-in action by calling the AuthHelper utility.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            LOG.warn("SignInAction performed without project context.");
            return;
        }

        // Check if already signed in before initiating
        if (AuthService.getInstance().isSignedIn()) {
            Notifications.Bus.notify(new Notification(
                    PluginConstants.NOTIFICATION_GROUP_ID,
                    "Already Signed In",
                    "You are already signed in.",
                    NotificationType.INFORMATION), project);
            return;
        }

        // --- Call the refactored helper method ---
        AuthHelper.initiateSignInFlow(project);

        // --- Inform User (Specific to this manual action) ---
       Notifications.Bus.notify(new Notification(
                PluginConstants.NOTIFICATION_GROUP_ID,
                "Sign In Initiated",
                "Please authorize the application in the opened browser window to complete sign in.",
                NotificationType.INFORMATION), project);
    }
}
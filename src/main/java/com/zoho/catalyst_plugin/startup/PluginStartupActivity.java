package com.zoho.catalyst_plugin.startup;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.service.AuthService;
import com.zoho.catalyst_plugin.service.OAuthCallbackService;

import kotlin.Unit;
import kotlin.coroutines.Continuation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Runs once per project after the project is initialized.
 * Implements ProjectActivity as required by postStartupActivity.
 * Checks if the user needs to sign in for the Catalyst LSK plugin
 * and prompts them via a notification if it's the first time for this project.
 */
public class PluginStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(PluginStartupActivity.class);
    private static final String AUTH_PROMPT_SHOWN_KEY = "com.zoho.catalyst_plugin.auth.prompted.v1";

    /**
     * The main execution method for ProjectActivity, matching the suspend function signature.
     * Since our logic here is synchronous, we add the Continuation parameter but don't
     * directly use it. We return null (equivalent to Unit for synchronous Java impl).
     */
    @Nullable // Method returns Object, which can be null
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        LOG.info("Catalyst LSK Plugin startup activity running for project: " + project.getName());

        AuthService authService = AuthService.getInstance();
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);

        // Conditions to show the prompt:
        // 1. User is not currently signed in.
        // 2. We haven't already shown this prompt for this specific project.
        if (!authService.isSignedIn() && !propertiesComponent.isTrueValue(AUTH_PROMPT_SHOWN_KEY)) {
            LOG.info("User is not signed in and prompt not shown previously for this project. Showing sign-in notification.");

            propertiesComponent.setValue(AUTH_PROMPT_SHOWN_KEY, true);
            LOG.debug("Set flag '" + AUTH_PROMPT_SHOWN_KEY + "' to true for project " + project.getName());

            // This call is synchronous and schedules the notification
            showSignInNotification(project);

        } else {
            if (authService.isSignedIn()) {
                LOG.info("User is already signed in. No prompt needed.");
            } else {
                LOG.info("Sign-in prompt already shown for this project previously. No prompt needed.");
            }
        }
        return null;
    }

    // showSignInNotification method remains the same
    private void showSignInNotification(@NotNull Project project) {
        Notification notification = new Notification(
                PluginConstants.NOTIFICATION_GROUP_ID,
                "Catalyst LSK Plugin Sign In",
                "Sign in with GitHub to resolve Logical Seed Keys (LSKs).",
                NotificationType.INFORMATION
        );

        AnAction signInAction = new AnAction("Sign In with GitHub...") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                LOG.info("Sign-in action triggered from notification.");
                if (AuthService.getInstance().isSignedIn()) {
                    Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Already Signed In", "You are already signed in.", NotificationType.INFORMATION), project);
                    notification.expire();
                    return;
                }

                LOG.info("Initiating GitHub OAuth flow from notification action...");
                String state = generateSecureRandomString(32);
                try {
                    OAuthCallbackService.setPendingState(state);
                    LOG.info("Generated and stored state parameter (temporarily): " + state);

                    String redirectUri = buildCallbackUrl();
                    LOG.debug("Using callback URL: " + redirectUri);

                    String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString());
                    String encodedScopes = URLEncoder.encode(PluginConstants.GITHUB_SCOPES, StandardCharsets.UTF_8.toString());

                    String authorizationUrl = String.format(
                            "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=%s&state=%s",
                            PluginConstants.GITHUB_CLIENT_ID,
                            encodedRedirectUri,
                            encodedScopes,
                            state
                    );

                    LOG.info("Opening browser to GitHub authorization URL...");
                    BrowserUtil.browse(authorizationUrl);
                    notification.expire();

                } catch (Exception ex) {
                    LOG.error("Failed to construct or open GitHub authorization URL from notification", ex);
                    Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Sign In Error", "Could not initiate sign-in process: " + ex.getMessage(), NotificationType.ERROR), project);
                    OAuthCallbackService.setPendingState(null);
                    notification.expire();
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(!AuthService.getInstance().isSignedIn());
            }
        };

        notification.addAction(signInAction);
        Notifications.Bus.notify(notification, project);
        LOG.info("Sign-in notification displayed.");
    }

    // --- Helper methods ---
    private String generateSecureRandomString(int byteLength) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildCallbackUrl() throws RuntimeException {
        BuiltInServerManager serverManager = BuiltInServerManager.getInstance();
        int port = serverManager.getPort();
        if (port < 0) {
            LOG.error("Built-in server port is not available (" + port + "). Cannot construct callback URL.");
            throw new RuntimeException("Could not get built-in server port for OAuth callback.");
        }
        String path = PluginConstants.GITHUB_CALLBACK_PATH.startsWith("/")
                ? PluginConstants.GITHUB_CALLBACK_PATH
                : "/" + PluginConstants.GITHUB_CALLBACK_PATH;
        return String.format("http://127.0.0.1:%d%s", port, path);
    }
}
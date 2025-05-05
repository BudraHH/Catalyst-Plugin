package com.zoho.catalyst_plugin.util;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.service.OAuthCallbackService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for handling common authentication-related tasks, like initiating sign-in.
 */
public class AuthHelper {

    private static final Logger LOG = Logger.getInstance(AuthHelper.class);

    /**
     * Initiates the GitHub OAuth sign-in flow by generating state, building the URL,
     * storing state, and opening the browser. Handles basic errors.
     *
     * @param project The current project context for notifications. Can be null, but notifications won't anchor.
     */
    public static void initiateSignInFlow(@NotNull Project project) {
        // Note: We don't check AuthService.isSignedIn() here, assuming the caller already did.
        LOG.info("Initiating GitHub OAuth flow via AuthHelper...");

        String state = generateSecureRandomString(32);
        try {
            // Store state using the callback service
            OAuthCallbackService.setPendingState(state);
            LOG.info("Generated and stored state parameter (temporarily): {}" + state);

            // Build URLs
            String redirectUri = buildCallbackUrl();
            LOG.debug("Using callback URL: {}", redirectUri);
            String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString());
            String encodedScopes = URLEncoder.encode(PluginConstants.GITHUB_SCOPES, StandardCharsets.UTF_8.toString());
            String authorizationUrl = String.format(
                    "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=%s&state=%s",
                    PluginConstants.GITHUB_CLIENT_ID,
                    encodedRedirectUri,
                    encodedScopes,
                    state
            );

            // Open browser
            LOG.info("Opening browser to GitHub authorization URL...");
            BrowserUtil.browse(authorizationUrl);

        } catch (Exception ex) {
            LOG.error("Failed to initiate GitHub sign-in flow", ex);
            Notifications.Bus.notify(new Notification(
                    PluginConstants.NOTIFICATION_GROUP_ID, "Sign In Error",
                    "Could not initiate sign-in process: " + ex.getMessage(), NotificationType.ERROR), project);
            // Clear potentially stored state on failure
            OAuthCallbackService.setPendingState(null);
        }
    }

    // --- Helper Methods (Moved from SignInAction/PluginStartupActivity) ---

    private static String generateSecureRandomString(int byteLength) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String buildCallbackUrl() throws RuntimeException {
        BuiltInServerManager serverManager = BuiltInServerManager.getInstance();
        int port = serverManager.getPort();
        if (port < 0) {
            LOG.error("Built-in server port is not available ({}). Cannot construct callback URL." + port);
            throw new RuntimeException("Could not get built-in server port for OAuth callback.");
        }
        String path = PluginConstants.GITHUB_CALLBACK_PATH.startsWith("/")
                ? PluginConstants.GITHUB_CALLBACK_PATH
                : "/" + PluginConstants.GITHUB_CALLBACK_PATH;
        return String.format("http://127.0.0.1:%d%s", port, path);
    }

    private AuthHelper() {}
}


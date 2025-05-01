package com.zoho.catalyst_plugin.service; // Keep your package

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
// import com.intellij.openapi.application.ModalityState; // Removed Import
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
// import com.intellij.util.messages.MessageBus; // Removed Import
import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.dto.AuthResponse;
// import com.zoho.catalyst_plugin.listeners.AuthenticationListener; // Removed Import
import com.zoho.catalyst_plugin.util.ResponseSender; // Ensure this path is correct
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.HttpRequestHandler; // Keep correct import


import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class OAuthCallbackService extends HttpRequestHandler {

    private static final Logger LOG = Logger.getInstance(OAuthCallbackService.class);
    private static final AtomicReference<String> pendingOAuthState = new AtomicReference<>(null);

    public static void setPendingState(@Nullable String state) {
        LOG.debug("Setting pending OAuth state: {}", (state != null ? state.substring(0, Math.min(state.length(), 10)) + "..." : "null"));
        pendingOAuthState.set(state);
    }

    @Override
    public boolean isSupported(@NotNull FullHttpRequest request) {
        boolean isGet = request.method() == HttpMethod.GET;
        String expectedPath = PluginConstants.GITHUB_CALLBACK_PATH.startsWith("/")
                ? PluginConstants.GITHUB_CALLBACK_PATH
                : "/" + PluginConstants.GITHUB_CALLBACK_PATH;
        boolean isCorrectPath = request.uri().startsWith(expectedPath);
        boolean supported = isGet && isCorrectPath;
        if (supported) {
            LOG.debug("OAuthCallbackService supports request: {}", request.uri());
        }
        return supported;
    }

    @Override
    public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
        LOG.info("Processing callback request: {}"+ request.uri());

        String expectedPath = PluginConstants.GITHUB_CALLBACK_PATH.startsWith("/")
                ? PluginConstants.GITHUB_CALLBACK_PATH
                : "/" + PluginConstants.GITHUB_CALLBACK_PATH;

        // --- Path Check ---
        if (!urlDecoder.path().equals(expectedPath)) {
            LOG.warn("Request path mismatch. Expected: {}, Got: {}"+ expectedPath+ urlDecoder.path());
            if (request.uri().startsWith(expectedPath)) {
                sendResponse(request, context, HttpResponseStatus.NOT_FOUND, "<html><body>Incorrect callback path.</body></html>");
                return true;
            } else {
                return false;
            }
        }

        // --- Parameter Extraction ---
        Map<String, List<String>> parameters = urlDecoder.parameters();
        String receivedCode = getParameter(parameters, "code");
        String receivedState = getParameter(parameters, "state");
        String error = getParameter(parameters, "error");
        String errorDescription = getParameter(parameters, "error_description");

        // --- Error Handling from GitHub ---
        if (error != null) {
            String errorMsg = "GitHub OAuth Error: " + error + (errorDescription != null ? " - " + errorDescription : "");
            LOG.warn(errorMsg);
            setPendingState(null);
            showNotification(NotificationType.ERROR, "GitHub Sign-In Failed", errorMsg);
            sendResponse(request, context, HttpResponseStatus.BAD_REQUEST, "<html><body>"+errorMsg+" You can close this page.</body></html>");
            return true;
        }

        // --- State Validation ---
        String expectedState = pendingOAuthState.getAndSet(null);
        if (expectedState == null) {
            LOG.error("OAuth callback received, but no pending state was found.");
            showNotification(NotificationType.ERROR, "Sign-In Error", "Security state mismatch (no pending state). Please try signing in again.");
            sendResponse(request, context, HttpResponseStatus.FORBIDDEN, "<html><body>Security error: No pending state. Please try signing in again. You can close this page.</body></html>");
            return true;
        }
        if (!expectedState.equals(receivedState)) {
            LOG.error("OAuth callback state mismatch! Expected: '{}', Received: '{}'.", expectedState, receivedState);
            showNotification(NotificationType.ERROR, "Sign-In Error", "Security state validation failed. Please try signing in again.");
            sendResponse(request, context, HttpResponseStatus.FORBIDDEN, "<html><body>Security error: State mismatch. Please try signing in again. You can close this page.</body></html>");
            return true;
        }
        LOG.info("OAuth state validated successfully.");

        // --- Code Validation ---
        if (receivedCode == null || receivedCode.trim().isEmpty()) {
            LOG.error("OAuth callback successful state validation, but no authorization code received.");
            showNotification(NotificationType.ERROR, "Sign-In Error", "Authorization code missing in GitHub response.");
            sendResponse(request, context, HttpResponseStatus.BAD_REQUEST, "<html><body>Error: Authorization code missing. You can close this page.</body></html>");
            return true;
        }

        // --- Exchange Code and Store Token ---
        try {
            LOG.info("Exchanging GitHub code for backend token...");
            BackendApiService backendService = BackendApiService.getInstance();
            AuthResponse authResponse = backendService.exchangeGitHubCode(receivedCode);

            if (authResponse != null && authResponse.getToken() != null && !authResponse.getToken().isEmpty()) {
                // Store the token
                AuthService.getInstance().storeAuthToken(authResponse.getToken());
                LOG.info("Successfully exchanged code and stored auth token.");

                // <<< publishAuthChangeEvent() call removed >>>

                // Show success notification and respond to browser
                showNotification(NotificationType.INFORMATION, "Sign-In Successful", "Catalyst LSK Plugin successfully signed in.");
                sendResponse(request, context, HttpResponseStatus.OK, "<html><body>Sign-in successful! You can close this page.</body></html>");

            } else {
                // Handle case where backend call succeeded but no token was returned
                String errMsg = "Sign-in failed: Backend did not return a valid token.";
                if (authResponse != null && authResponse.getMessage() != null) { errMsg += " Message: " + authResponse.getMessage(); }
                LOG.error(errMsg + " AuthResponse: {}"+ authResponse);
                showNotification(NotificationType.ERROR, "Sign-In Failed", errMsg);
                sendResponse(request, context, HttpResponseStatus.INTERNAL_SERVER_ERROR, "<html><body>Sign-in failed (token processing). Please try again or contact support. You can close this page.</body></html>");
            }

        } catch (Exception e) { // Catch errors from backendService.exchangeGitHubCode
            LOG.error("Failed to exchange GitHub code with backend", e);
            showNotification(NotificationType.ERROR, "Sign-In Failed", "Could not connect to backend or process response: " + e.getMessage());
            sendResponse(request, context, HttpResponseStatus.INTERNAL_SERVER_ERROR, "<html><body>Sign-in failed (backend communication). Please try again or contact support. You can close this page.</body></html>");
        }

        return true; // Handled
    }

    // --- Helper methods (getParameter, sendResponse, showNotification) remain the same ---
    private String getParameter(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values != null && !values.isEmpty()) { return values.get(0); }
        return null;
    }

    private void sendResponse(@NotNull FullHttpRequest req, @NotNull ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        // Make sure ResponseSender class is accessible
        ResponseSender.sendResponse(req, ctx, status, content, "text/html; charset=UTF-8");
    }

    private void showNotification(NotificationType type, String title, String content) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Project project = ProjectManager.getInstance().getDefaultProject();
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) { project = openProjects[0]; }
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, title, content, type), project);
            LOG.debug("Showing notification: [{}] {} - {}", type, title, content);
        });
    }

    // <<< publishAuthChangeEvent() method REMOVED >>>

}
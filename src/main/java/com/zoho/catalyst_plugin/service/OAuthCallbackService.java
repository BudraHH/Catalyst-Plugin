package com.zoho.catalyst_plugin.service; // Keep your package

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
// Keep this import if needed for parameter parsing, or switch to QueryStringDecoder methods directly
// import com.intellij.util.io.HttpRequests;
import com.zoho.catalyst_plugin.util.ResponseSender; // Make sure path to ResponseSender is correct
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
// Remove the incorrect import
// import org.jetbrains.builtInWebServer.HttpRequestHandler;
// Import the CORRECT HttpRequestHandler
import org.jetbrains.ide.HttpRequestHandler; // <<< CHANGE HERE

// Import your existing services and constants
import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.dto.AuthResponse;
import com.zoho.catalyst_plugin.service.AuthService;
import com.zoho.catalyst_plugin.service.BackendApiService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles the HTTP callback from the GitHub OAuth flow, running on the IDE's built-in server.
 * Implements org.jetbrains.ide.HttpRequestHandler as required by the extension point.
 * Processes the request, validates the state, exchanges the code for a token,
 * stores the token, and provides user feedback.
 */
// Extend the CORRECT HttpRequestHandler
public class OAuthCallbackService extends HttpRequestHandler { // <<< CHANGE HERE

    private static final Logger LOG = Logger.getInstance(OAuthCallbackService.class);

    private static final AtomicReference<String> pendingOAuthState = new AtomicReference<>(null);

    public static void setPendingState(@Nullable String state) {
        LOG.debug("Setting pending OAuth state: " + (state != null ? state.substring(0, Math.min(state.length(), 10)) + "..." : "null"));
        pendingOAuthState.set(state);
    }

    /**
     * Check if this request handler is applicable.
     * Checks method and URI prefix.
     */
    @Override
    public boolean isSupported(@NotNull FullHttpRequest request) {
        // The methods and logic required by org.jetbrains.ide.HttpRequestHandler
        // are often the same as org.jetbrains.builtInWebServer.HttpRequestHandler
        // for basic checks.
        boolean isGet = request.method() == HttpMethod.GET;
        // Ensure the callback path constant has a leading slash for reliable startsWith check
        String expectedPath = PluginConstants.GITHUB_CALLBACK_PATH.startsWith("/")
                ? PluginConstants.GITHUB_CALLBACK_PATH
                : "/" + PluginConstants.GITHUB_CALLBACK_PATH;
        boolean isCorrectPath = request.uri().startsWith(expectedPath); // Check prefix

        boolean supported = isGet && isCorrectPath;
        if (supported) {
            LOG.debug("OAuthCallbackService supports request: " + request.uri());
        }
        return supported;
    }


    /**
     * Process the request. Return true if processed, false otherwise.
     */
    @Override
    public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
        LOG.info("Processing callback request: " + request.uri());

        // Ensure the callback path constant has a leading slash
        String expectedPath = PluginConstants.GITHUB_CALLBACK_PATH.startsWith("/")
                ? PluginConstants.GITHUB_CALLBACK_PATH
                : "/" + PluginConstants.GITHUB_CALLBACK_PATH;

        // Check if it's the exact path we expect (QueryStringDecoder handles query part)
        if (!urlDecoder.path().equals(expectedPath)) {
            LOG.warn("Request path mismatch. Expected: " + expectedPath + ", Got: " + urlDecoder.path());
            // Let other handlers potentially process it, or send Not Found if this is the ONLY handler for this prefix
            // For safety, let's respond definitively if the prefix matched but the full path didn't.
            if (request.uri().startsWith(expectedPath)) { // Check prefix again
                sendResponse(request, context, HttpResponseStatus.NOT_FOUND, "<html><body>Incorrect callback path.</body></html>");
                return true; // We handled it (by rejecting it)
            } else {
                return false; // Let other handlers try
            }
        }

        // --- The rest of the process method remains the same ---
        Map<String, List<String>> parameters = urlDecoder.parameters();
        String receivedCode = getParameter(parameters, "code");
        String receivedState = getParameter(parameters, "state");
        String error = getParameter(parameters, "error");
        String errorDescription = getParameter(parameters, "error_description");

        // --- Handle potential errors reported by GitHub ---
        if (error != null) {
            String errorMsg = "GitHub OAuth Error: " + error + (errorDescription != null ? " - " + errorDescription : "");
            LOG.warn(errorMsg);
            setPendingState(null); // Clear state as the flow failed
            showNotification(NotificationType.ERROR, "GitHub Sign-In Failed", errorMsg);
            sendResponse(request, context, HttpResponseStatus.BAD_REQUEST, "<html><body>" + errorMsg + " You can close this page.</body></html>");
            return true; // Handled
        }

        // --- Validate State ---
        String expectedState = pendingOAuthState.getAndSet(null); // Atomically get and clear the expected state

        if (expectedState == null) {
            LOG.error("OAuth callback received, but no pending state was found. Possible CSRF or state cleared prematurely.");
            showNotification(NotificationType.ERROR, "Sign-In Error", "Security state mismatch (no pending state). Please try signing in again.");
            sendResponse(request, context, HttpResponseStatus.FORBIDDEN, "<html><body>Security error: No pending state. Please try signing in again. You can close this page.</body></html>");
            return true; // Handled
        }

        if (!expectedState.equals(receivedState)) {
            LOG.error("OAuth callback state mismatch! Expected: '" + expectedState + "', Received: '" + receivedState + "'. Possible CSRF attack.");
            showNotification(NotificationType.ERROR, "Sign-In Error", "Security state validation failed. Please try signing in again.");
            sendResponse(request, context, HttpResponseStatus.FORBIDDEN, "<html><body>Security error: State mismatch. Please try signing in again. You can close this page.</body></html>");
            return true; // Handled
        }

        LOG.info("OAuth state validated successfully.");

        // --- Validate Code ---
        if (receivedCode == null || receivedCode.trim().isEmpty()) {
            LOG.error("OAuth callback successful state validation, but no authorization code received.");
            showNotification(NotificationType.ERROR, "Sign-In Error", "Authorization code missing in GitHub response.");
            sendResponse(request, context, HttpResponseStatus.BAD_REQUEST, "<html><body>Error: Authorization code missing. You can close this page.</body></html>");
            return true; // Handled
        }

        // --- Exchange Code for Token ---
        try {
            LOG.info("Exchanging GitHub code for backend token...");
            BackendApiService backendService = BackendApiService.getInstance();
            AuthResponse authResponse = backendService.exchangeGitHubCode(receivedCode);

            // --- Store Token ---
            if (authResponse != null && authResponse.getToken() != null && !authResponse.getToken().isEmpty()) {
                AuthService.getInstance().storeAuthToken(authResponse.getToken());
                LOG.info("Successfully exchanged code and stored auth token.");
                showNotification(NotificationType.INFORMATION, "Sign-In Successful", "Catalyst LSK Plugin successfully signed in.");
                sendResponse(request, context, HttpResponseStatus.OK, "<html><body>Sign-in successful! You can close this page.</body></html>");
            } else {
                String errMsg = "Sign-in failed: Backend did not return a valid token.";
                if (authResponse != null && authResponse.getMessage() != null) {
                    errMsg += " Message: " + authResponse.getMessage();
                }
                LOG.error(errMsg + " AuthResponse: " + authResponse);
                showNotification(NotificationType.ERROR, "Sign-In Failed", errMsg);
                sendResponse(request, context, HttpResponseStatus.INTERNAL_SERVER_ERROR, "<html><body>Sign-in failed (token processing). Please try again or contact support. You can close this page.</body></html>");
            }

        } catch (Exception e) {
            LOG.error("Failed to exchange GitHub code with backend", e);
            showNotification(NotificationType.ERROR, "Sign-In Failed", "Could not connect to backend or process response: " + e.getMessage());
            sendResponse(request, context, HttpResponseStatus.INTERNAL_SERVER_ERROR, "<html><body>Sign-in failed (backend communication). Please try again or contact support. You can close this page.</body></html>");
        }

        return true; // We handled the request
    }


    // Helper methods (getParameter, sendResponse, showNotification) remain the same
    private String getParameter(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    private void sendResponse(@NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context, HttpResponseStatus status, String htmlContent) {
        // Make sure ResponseSender class is accessible (e.g., in com.zoho.catalyst_plugin.util package)
        ResponseSender.sendResponse(request, context, status, htmlContent, "text/html; charset=UTF-8");
    }


    private void showNotification(NotificationType type, String title, String content) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Project project = ProjectManager.getInstance().getDefaultProject();
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                project = openProjects[0];
            }
            Notifications.Bus.notify(new Notification(
                    PluginConstants.NOTIFICATION_GROUP_ID,
                    title,
                    content,
                    type
            ), project);
            LOG.debug("Showing notification: [" + type + "] " + title + " - " + content);
        });
    }
}
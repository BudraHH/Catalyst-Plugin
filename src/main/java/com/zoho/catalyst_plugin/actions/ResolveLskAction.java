package com.zoho.catalyst_plugin.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.util.AuthHelper;
import org.jetbrains.annotations.NotNull;

import com.zoho.catalyst_plugin.dto.ApiResponse;
import com.zoho.catalyst_plugin.service.AuthService;
import com.zoho.catalyst_plugin.service.BackendApiService;

import java.io.IOException;

/**
 * Action triggered to resolve LSK placeholders in the selected XML text
 * by calling the backend service. Handles getting selected text, authentication,
 * background API call, and updating the editor or showing notifications.
 **/
public class ResolveLskAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(ResolveLskAction.class);

    // ID for group
    public static final String NOTIFICATION_GROUP_ID = "CatalystPluginNotifications"; // Match plugin.xml

    /**
     * Enables/disables the action based on context.
     * Action is enabled only if a project is open, an editor is active,
     * the active file is an XML file, and text is selected.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        boolean enabled = project != null &&
                editor != null &&
                editor.getSelectionModel().hasSelection() &&
                psiFile instanceof XmlFile;

        e.getPresentation().setEnabledAndVisible(enabled);
        // auth check
        // e.getPresentation().setEnabled(enabled && AuthService.getInstance().isSignedIn());
    }

    /**
     * Executes when the user invokes the action.
     **/
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        LOG.info("Resolve LSK Action Triggered!");

        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);

        // --- Get Context and Selected Text ---
        if (project == null) {
            LOG.warn("Action performed without project.");
            return;
        }
        if (editor == null) {
            LOG.warn("Action performed without editor.");
            return;
        }
        if (!editor.getSelectionModel().hasSelection()) {
            LOG.warn("Action performed without text selection.");
            return;
        }

        final SelectionModel selectionModel = editor.getSelectionModel();
        final String selectedText = selectionModel.getSelectedText();

        if (selectedText == null || selectedText.trim().isEmpty()) {
            Notifications.Bus.notify(new Notification(NOTIFICATION_GROUP_ID, "No Text Selected", "Please select the XML block to resolve.", NotificationType.WARNING), project);
            return;
        }

        // Store document and selection offsets for use after background task
        final Document document = editor.getDocument();
        final int startOffset = selectionModel.getSelectionStart();
        final int endOffset = selectionModel.getSelectionEnd();

        // --- Get Authentication Token ---
        final String sessionToken = AuthService.getInstance().getAuthToken();
        if (sessionToken == null) {
            LOG.warn("Auth token is null. User needs to sign in.");
            Notifications.Bus.notify(new Notification(NOTIFICATION_GROUP_ID, "Not Signed In", "Please sign in first (e.g., via Tools menu).", NotificationType.ERROR), project);
            return;
        }
        LOG.debug("Retrieved auth token for API call.");

        // --- Execute API Call in Background Task ---
        new Task.Backgroundable(project, "Resolving LSKs...", true) {
            ApiResponse apiResponse = null;

            /**
             * Runs on background thread. Makes API call.
             */
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Contacting LSK resolution service...");
                LOG.info("Background task started for LSK resolution.");

                try {
                    // --- Replace with actual call to your implemented BackendApiService ---
                    apiResponse = BackendApiService.getInstance()
                            .resolveLskSelection(selectedText, sessionToken);

                    if (apiResponse == null) {
                        throw new IOException("Received null response object from Backend API Service.");
                    }

                    // Log outcome based on response content
                    if (apiResponse.getError() != null) {
                        LOG.warn("Backend returned an error: " + apiResponse.getError());
                    } else if (apiResponse.getData() != null) {
                        LOG.info("Backend successfully resolved LSKs.");
                    } else {
                        LOG.warn("Backend returned success status but no data or error message.");
                        // Ensure error field is populated for UI feedback
                        apiResponse.error = "Received unexpected empty success response from server.";
                    }

                } catch (IOException ioException) { // Catch specific Network/IO errors
                    LOG.error("Network or IO error calling LSK backend API", ioException);
                    apiResponse = new ApiResponse("Network Error: " + ioException.getMessage()); // Create error response
                } catch (Exception e) { // Catch other unexpected errors from service/parsing
                    LOG.error("Unexpected error during LSK resolution task", e);
                    apiResponse = new ApiResponse("Plugin Error: " + e.getMessage()); // Create error response
                }
            }

            // Inside ResolveLskAction's Task.Backgroundable

            /**
             * Runs on UI thread (EDT) after run() completes. Updates editor or shows notification.
             */
            @Override
            public void onSuccess() {
                LOG.info("LSK resolution task finished on background thread.");
                if (apiResponse == null) {
                    LOG.error("Internal Error: onSuccess called but apiResponse is null!");
                    Notifications.Bus.notify(new Notification(
                            PluginConstants.NOTIFICATION_GROUP_ID, // Use constant
                            "Plugin Error",
                            "Internal error: No response data processed.",
                            NotificationType.ERROR), project);
                    return;
                }

                // Check if the API call resulted in resolved data
                if (apiResponse.getData() != null) {
                    // --- SUCCESS: Update Editor ---
                    final String resolvedXml = apiResponse.getData();
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        if (!editor.isDisposed() && document.isWritable()) {
                            try {
                                document.replaceString(startOffset, endOffset, resolvedXml);
                                Notifications.Bus.notify(new Notification(
                                        PluginConstants.NOTIFICATION_GROUP_ID, // Use constant
                                        "LSKs Resolved",
                                        "Selection updated successfully.",
                                        NotificationType.INFORMATION), project);
                                LOG.info("Editor content updated successfully.");
                            } catch (Exception ex) {
                                LOG.error("Failed to replace document text", ex);
                                Notifications.Bus.notify(new Notification(
                                        PluginConstants.NOTIFICATION_GROUP_ID, // Use constant
                                        "Update Error",
                                        "Could not update editor content: " + ex.getMessage(),
                                        NotificationType.ERROR), project);
                            }
                        } else {
                            LOG.warn("Editor was disposed or document not writable before replacement could occur.");
                            Notifications.Bus.notify(new Notification(
                                    PluginConstants.NOTIFICATION_GROUP_ID, // Use constant
                                    "Update Warning",
                                    "Editor changed before update could be applied.",
                                    NotificationType.WARNING), project);
                        }
                    });
                } else {
                    // --- FAILURE: Handle Error ---
                    String errorMsg = apiResponse.getError() != null ? apiResponse.getError() : "Unknown error occurred.";
                    LOG.warn("LSK Resolution failed on backend: {}" + errorMsg);

                    // <<< START NEW LOGIC >>>
                    // Check if it's likely an authentication error (based on backend message)
                    boolean isAuthError = errorMsg.contains("(HTTP Status: 401)") ||
                            errorMsg.contains("Invalid or expired session token"); // Add other specific phrases if needed

                    if (isAuthError) {
                        // --- AUTHENTICATION FAILURE ---
                        LOG.info("Authentication error detected during LSK resolution. Clearing stored token and prompting for re-login.");

                        // Clear the invalid token from local storage
                        AuthService.getInstance().clearAuthToken();

                        // Create notification prompting the user to sign in again
                        Notification reAuthNotification = new Notification(
                                PluginConstants.NOTIFICATION_GROUP_ID, // Use constant
                                "Authentication Required",
                                "Your session is invalid or has expired. Please sign in again.\nDetails: " + errorMsg,
                                NotificationType.ERROR // Use ERROR to indicate the previous action failed
                        );

                        // Add a "Sign In" button to the notification
                        AnAction signInAction = new AnAction("Sign In with GitHub...") {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                                // Call the refactored helper to start the sign-in flow
                                // Ensure AuthHelper is imported: import com.zoho.catalyst_plugin.util.AuthHelper;
                                AuthHelper.initiateSignInFlow(project);
                                reAuthNotification.expire(); // Close this notification once action is taken
                            }

                            // Optional: Disable if user somehow signs in before clicking
                            @Override
                            public void update(@NotNull AnActionEvent e) {
                                e.getPresentation().setEnabled(!AuthService.getInstance().isSignedIn());
                            }
                        };
                        reAuthNotification.addAction(signInAction);

                        // Show the re-authentication notification
                        Notifications.Bus.notify(reAuthNotification, project);

                    } else {
                        // --- OTHER BACKEND/PLUGIN FAILURE ---
                        // Show the original generic error notification
                        Notifications.Bus.notify(new Notification(
                                PluginConstants.NOTIFICATION_GROUP_ID, // Use constant
                                "LSK Resolution Failed",
                                errorMsg, // Show the specific error from the backend
                                NotificationType.ERROR
                        ), project);
                    }
                    // <<< END NEW LOGIC >>>
                }
            } // End onSuccess

            // onThrowable method remains the same
            @Override
            public void onThrowable(@NotNull Throwable error) {
                // ... existing onThrowable logic ...
                LOG.error("Unhandled throwable in LSK background task", error);
                Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Plugin Error", "An internal plugin error occurred: " + error.getMessage(), NotificationType.ERROR), project);
            }

        }.queue(); // Queue the task for execution by the IDE's background task manager
    }
}
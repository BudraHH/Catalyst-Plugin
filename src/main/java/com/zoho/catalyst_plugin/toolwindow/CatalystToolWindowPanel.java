package com.zoho.catalyst_plugin.toolwindow;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger; // Use Logger
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
// import com.intellij.openapi.Disposable; // <<< Removed Import
// import com.intellij.openapi.util.Disposer; // <<< Removed Import
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
// import com.intellij.util.messages.MessageBusConnection; // <<< Removed Import
import com.zoho.catalyst_plugin.config.PluginConstants;
// import com.zoho.catalyst_plugin.listeners.AuthenticationListener; // <<< Removed Import
import com.zoho.catalyst_plugin.service.AuthService;
import com.zoho.catalyst_plugin.util.AuthHelper; // Use our helper
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * The UI panel displayed inside the Catalyst LSK Tool Window.
 * Contains controls for sign-in/out, displays status.
 * Does NOT automatically update on external auth changes without MessageBus.
 */
// <<< No longer implements Disposable >>>
public class CatalystToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(CatalystToolWindowPanel.class);

    private final Project project;
    private final ToolWindow toolWindow;

    // --- UI Components ---
    private JPanel mainPanel;
    private JLabel statusLabel;
    private JButton signInSignOutButton;
    private JButton resolveButton;

    // <<< Removed MessageBusConnection field >>>
    // private MessageBusConnection connection;

    public CatalystToolWindowPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        LOG.debug("Creating CatalystToolWindowPanel for project: {}", project.getName());
        initComponents();
        // <<< Removed call to subscribeToAuthChanges() >>>
        updateUIState(); // Set initial state

        // <<< Removed Disposer.register call >>>
        // Disposer.register(toolWindow.getDisposable(), this);
    }

    /**
     * Creates and lays out the Swing components for the tool window panel.
     */
    private void initComponents() {
        // ... (initComponents implementation remains the same) ...
        mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statusLabel = new JLabel("Status: Initializing...");
        mainPanel.add(statusLabel, BorderLayout.NORTH);
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resolveButton = new JButton("Resolve LSKs in Selection");
        resolveButton.setToolTipText("Resolve selected LSK placeholders in the active XML editor.");
        resolveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resolveButton.setVisible(false);
        buttonPanel.add(resolveButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        signInSignOutButton = new JButton("...");
        signInSignOutButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.add(signInSignOutButton);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        signInSignOutButton.addActionListener(this::handleSignInSignOut);
        resolveButton.addActionListener(this::handleResolveLsk);
    }

    /**
     * Updates the text and visibility/enabled state of UI components based on auth status.
     * Ensures updates happen on the Swing Event Dispatch Thread.
     */
    public void updateUIState() {
        // ... (updateUIState implementation remains the same) ...
        SwingUtilities.invokeLater(() -> {
            boolean isSignedIn = AuthService.getInstance().isSignedIn();
            LOG.debug("Updating UI state. Is signed in: {}", isSignedIn);
            if (isSignedIn) {
                statusLabel.setText("Status: Signed In");
                signInSignOutButton.setText("Sign Out");
                signInSignOutButton.setToolTipText("Sign out from the Catalyst LSK service.");
                resolveButton.setVisible(true);
                resolveButton.setEnabled(true);
            } else {
                statusLabel.setText("Status: Not Signed In");
                signInSignOutButton.setText("Sign In with GitHub...");
                signInSignOutButton.setToolTipText("Initiate sign in using your GitHub account.");
                resolveButton.setVisible(false);
                resolveButton.setEnabled(false);
            }
            signInSignOutButton.setEnabled(true);
        });
    }

    /**
     * Action handler for the Sign In / Sign Out button. Provides immediate feedback
     * during sign-in initiation and allows retrying if needed.
     */
    private void handleSignInSignOut(ActionEvent e) {
        // ... (handleSignInSignOut implementation remains the same,
        //      including the fix for re-enabling the button after sign-in attempt) ...
        AuthService authService = AuthService.getInstance();
        if (authService.isSignedIn()) {
            // --- Sign Out ---
            LOG.info("Sign Out button clicked.");
            authService.clearAuthToken();
            // <<< Removed publishAuthChangeEvent() call >>>
            Notifications.Bus.notify(new Notification(
                    PluginConstants.NOTIFICATION_GROUP_ID, "Signed Out",
                    "You have been signed out.", NotificationType.INFORMATION), project);
            updateUIState(); // Manually update UI after sign out

        } else {
            // --- Sign In ---
            LOG.info("Sign In button clicked.");
            final String originalButtonText = signInSignOutButton.getText();
            final String originalStatusText = "Status: Not Signed In";
            signInSignOutButton.setEnabled(false);
            statusLabel.setText("Status: Opening browser...");
            AuthHelper.initiateSignInFlow(project);
            SwingUtilities.invokeLater(() -> {
                if (!AuthService.getInstance().isSignedIn()) {
                    LOG.debug("Resetting sign-in button/status after initiation attempt.");
                    statusLabel.setText(originalStatusText);
                    signInSignOutButton.setText(originalButtonText);
                    signInSignOutButton.setEnabled(true);
                } else {
                    // NOTE: This else block might not reliably reflect the
                    // actual signed-in state immediately after the flow,
                    // as there's no event triggering this check at the right time.
                    // It relies on the unlikely chance that invokeLater runs
                    // *after* the entire OAuth flow completes AND the token is stored.
                    LOG.debug("Sign-in might have completed, calling updateUIState.");
                    updateUIState();
                }
            });
        }
    }

    /**
     * Action handler for the "Resolve LSKs in Selection" button.
     */
    private void handleResolveLsk(ActionEvent e) {
        // ... (handleResolveLsk implementation remains the same) ...
        LOG.debug("Resolve LSK button clicked.");
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) { Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID,"LSK Resolver","No active text editor found.", NotificationType.WARNING), project); return; }
        Document document = editor.getDocument();
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (virtualFile == null) { Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID,"LSK Resolver","Cannot identify file.", NotificationType.WARNING), project); return; }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (!(psiFile instanceof XmlFile)) { Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID,"LSK Resolver","Please open an XML file.", NotificationType.WARNING), project); return; }
        if (!editor.getSelectionModel().hasSelection()) { Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID,"LSK Resolver","Please select XML text.", NotificationType.WARNING), project); return; }
        Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "LSK Resolver", "Context is valid! Please use the right-click context menu:\n'Catalyst LSK' -> 'Resolve LSKs in Selection'\non your selected text.", NotificationType.INFORMATION), project);
    }


    /**
     * Returns the main JPanel to be added to the tool window content manager.
     */
    public JPanel getMainPanel() {
        return mainPanel;
    }

    /**
     * Refreshes the UI state based on current authentication status.
     * Can be called externally (e.g., after sign-in completes).
     */
    public void refreshUI() {
        // This method still exists but won't be called automatically by MessageBus
        LOG.debug("Explicit refreshUI called.");
        updateUIState();
    }

    // <<< REMOVED MessageBus Subscription method >>>
    // private void subscribeToAuthChanges() { ... }

    // <<< REMOVED Disposable Implementation and dispose() method >>>
    // @Override public void dispose() { ... }

    // <<< REMOVED publishAuthChangeEvent() helper method >>>
    // private void publishAuthChangeEvent() { ... }
}
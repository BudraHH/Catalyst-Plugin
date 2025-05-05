package com.zoho.catalyst_plugin.toolwindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import com.intellij.collaboration.ui.JPanelWithBackground;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.messages.MessageBusConnection;

import com.zoho.catalyst_plugin.config.PluginConstants;
import com.zoho.catalyst_plugin.dto.ApiResponse;
import com.zoho.catalyst_plugin.listeners.AuthenticationListener;
import com.zoho.catalyst_plugin.service.AuthService;
import com.zoho.catalyst_plugin.service.BackendApiService;
import com.zoho.catalyst_plugin.util.AuthHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;


public class CatalystToolWindowPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(CatalystToolWindowPanel.class);

    private final Project project;
    private final ToolWindow toolWindow;

    // --- UI Components ---
    private JPanel mainPanel;
    private JLabel statusLabel;
    private JButton signInButton;
    private JPanel signInButtonPanel;
    private JButton resolveButton;
    private JBTextField moduleNameField;
    private JPanel formContentPanel;
    private JPanel signedInContentPanel;
    private JBLabel infoLabel;
    private JPanelWithBackground usageGuidelinesPanel;
    private JPanelWithBackground placeholderGuidelinesPanel;
    private String defaultModuleName;
    private String currentModuleName;
    private JBLabel currentModuleNameLabel;

    private MessageBusConnection connection;

    private void setBaseDirectory(String val){
        this.defaultModuleName = val;
    }

    private void setCurrentModuleName(String val){
        this.currentModuleName = val;
    }

    public CatalystToolWindowPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.defaultModuleName = getProjectDirectoryName();
        LOG.debug("Creating CatalystToolWindowPanel for project: {}", project.getName());
        mainPanel = createMainPanel(); // Creates the root panel and child components
        subscribeToAuthChanges();
        updateUIState(); // Set initial visibility based on auth state
        Disposer.register(toolWindow.getDisposable(), this);
    }

    @Nullable
    private String getProjectDirectoryName() {
        VirtualFile baseDir = project.getBaseDir();
        return (baseDir != null) ? baseDir.getName() : null;
    }

    private JPanel createMainPanel() {
        // Status Label (will be added to signedInContentPanel)
        statusLabel = new JBLabel("Status: Initializing...");

        // Sign-in Button and its Centering Panel
        signInButton = new JButton("Sign In with GitHub");
        signInButton.setToolTipText("Initiate sign in using your GitHub account.");
        signInButton.addActionListener(this::handleSignIn);
        signInButton.setMargin(JBUI.insets(5, 15));
        signInButtonPanel = new JPanel(new GridBagLayout()); // Use GridBagLayout for centering
        signInButtonPanel.add(signInButton); // Add button, GridBagLayout will center by default

        this.currentModuleNameLabel = new JBLabel("N/A"); // Initialize
        currentModuleNameLabel.setForeground(UIUtil.getContextHelpForeground());
        currentModuleNameLabel.setToolTipText("Relative path of the currently focused XML file.");

        // Components for the Signed-in View
        moduleNameField = new JBTextField();
        moduleNameField.setToolTipText("Optional: Enter a default Module name if not specified in placeholders.");

        resolveButton = new JButton("Resolve");
        resolveButton.setToolTipText("Resolve selected LSK placeholders in the active XML editor using the specified module.");
        resolveButton.addActionListener(this::handleResolveLsk);

        infoLabel = new JBLabel("<html><i>Select XML in the editor, specify module (optional), then click 'Resolve'.</i></html>");
        infoLabel.setForeground(UIUtil.getContextHelpForeground());

        // --- Usage Guidelines Panel ---
        usageGuidelinesPanel = new JPanelWithBackground(new BorderLayout());
        usageGuidelinesPanel.setBorder(IdeBorderFactory.createTitledBorder("Usage Guide", false, JBUI.insetsTop(8)));
        JBTextArea usageGuidelinesText = new JBTextArea();
        usageGuidelinesText.setEditable(false);
        usageGuidelinesText.setBackground(UIUtil.getPanelBackground());
        usageGuidelinesText.setWrapStyleWord(true);
        usageGuidelinesText.setLineWrap(true);
        usageGuidelinesText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.NORMAL));
        usageGuidelinesText.setForeground(UIUtil.getContextHelpForeground());
        usageGuidelinesText.setText(
                "1. Select XML block with LSK placeholders in the editor.\n (e.g., <Element id=\"Table:Column:Module:LogicalID\"/>)\n" +
                        "2. Optionally enter 'Module Name' above to override default. \n(Default module: '" + (this.currentModuleName != null ? this.currentModuleName : "N/A") + "')\n" +
                        "3. Click 'Resolve LSKs in Selection'.\n" +
                        "4. Selection replaced with resolved values (e.g., ...:123\"/>)."
        );
        usageGuidelinesPanel.add(usageGuidelinesText, BorderLayout.CENTER);

        // --- Placeholders Guidelines Panel ---
        placeholderGuidelinesPanel = new JPanelWithBackground(new BorderLayout());
        placeholderGuidelinesPanel.setBorder(IdeBorderFactory.createTitledBorder("Placeholders Guidelines", false, JBUI.insetsTop(8)));
        JBTextArea placeholderGuidelinesText = new JBTextArea();
        // ... (configure placeholderGuidelinesText as before) ...
        placeholderGuidelinesText.setEditable(false);
        placeholderGuidelinesText.setBackground(UIUtil.getPanelBackground());
        placeholderGuidelinesText.setWrapStyleWord(true);
        placeholderGuidelinesText.setLineWrap(true);
        placeholderGuidelinesText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.NORMAL));
        placeholderGuidelinesText.setForeground(UIUtil.getContextHelpForeground());
        placeholderGuidelinesText.setText(
                "LSK Placeholders (within XML attribute values):\n\n" +
                        "Primary Key (LSK):----------------------------------\n" +
                        "  Format: \"Table:Column:Module:LogicalID\"\n" +
                        "  Example: <Product product_id=\"Products:ProductID:Inventory:PROD_XYZ\"/>\n" +
                        "  -> Resolves to: \"Products:ProductID:Inventory:123\"\n\n" +
                        "Foreign Key (REF):----------------------------------\n" +
                        "  Format: \"REF:{TargetTable:TargetCol:TargetMod:TargetLogicalID}\"\n" +
                        "  -> Target must match an LSK placeholder in the selection.\n" +
                        "  Example: <OrderLine item_id=\"REF:{Products:ProductID:Inventory:PROD_XYZ}\"/>\n" +
                        "  -> Resolves to the LSK value (e.g., \"Products:ProductID:Inventory:123\")"
        );
        // Wrap the text area in a scroll pane BEFORE adding to the placeholder panel
        placeholderGuidelinesPanel.add(placeholderGuidelinesText, BorderLayout.CENTER);

        // 4. Panel containing the form elements (built by FormBuilder)
        formContentPanel = FormBuilder.createFormBuilder()
                .setAlignLabelOnRight(false)
//                .addLabeledComponent(new JBLabel("Default Module Name:"), new JBLabel(defaultModuleName != null ? defaultModuleName : "N/A"), JBUI.scale(10))
                .addLabeledComponent(new JBLabel("Current Module:"), currentModuleNameLabel, JBUI.scale(10))
                .addLabeledComponent(new JBLabel("Module Name (Override):"), moduleNameField, JBUI.scale(10))
                .addComponent(resolveButton, JBUI.scale(10))
                .addComponent(infoLabel, JBUI.scale(10))
                .addComponent(usageGuidelinesPanel, JBUI.scale(15)) // Increase spacing slightly
                .addComponent(placeholderGuidelinesPanel, JBUI.scale(10))
                .getPanel();

        // 5. Panel combining status label and form content for the "Signed In" state
        signedInContentPanel = new JBPanel<>(new BorderLayout(0, 5)); // BorderLayout for status (N) + form (C)
        signedInContentPanel.setBorder(JBUI.Borders.emptyBottom(5)); // Add padding at the bottom
        signedInContentPanel.add(statusLabel, BorderLayout.NORTH);
        // Add the form content inside a scroll pane to handle potentially long content
        signedInContentPanel.add(ScrollPaneFactory.createScrollPane(formContentPanel, true), BorderLayout.CENTER);

        // 6. The main root panel using BorderLayout
        JBPanel<JBPanel> rootPanel = new JBPanel<>(new BorderLayout(0, 0)); // No gaps
        rootPanel.setBorder(JBUI.Borders.empty(10));
        rootPanel.add(signedInContentPanel, BorderLayout.NORTH);

        return rootPanel;
    }

    public void updateUIState() {
        SwingUtilities.invokeLater(() -> {
            boolean isSignedIn = AuthService.getInstance().isSignedIn();
            LOG.debug("Updating UI state. Is signed in: {}", isSignedIn);

            // Always update status text
            if (isSignedIn) {
                statusLabel.setText("Status: Signed In");
            } else {
                statusLabel.setText("Status: Not Signed In"); // Update even if hidden
            }

            // Get current components in mainPanel
            BorderLayout layout = (BorderLayout) mainPanel.getLayout();
            Component centerComponent = layout.getLayoutComponent(BorderLayout.CENTER);
            Component northComponent = layout.getLayoutComponent(BorderLayout.NORTH);

            if (isSignedIn) {
                // Ensure signedInContentPanel is NORTH, remove signInButtonPanel from CENTER
                if (centerComponent == signInButtonPanel) mainPanel.remove(signInButtonPanel);
                if (northComponent != signedInContentPanel) {
                    if (northComponent != null) mainPanel.remove(northComponent); // Remove anything else from NORTH
                    mainPanel.add(signedInContentPanel, BorderLayout.NORTH);
                }
                // Set visibility (though it's managed by adding/removing)
                signedInContentPanel.setVisible(true);
                signInButtonPanel.setVisible(false);
            } else {
                // Ensure signInButtonPanel is CENTER, remove signedInContentPanel from NORTH
                if (northComponent == signedInContentPanel) mainPanel.remove(signedInContentPanel);
                if (centerComponent != signInButtonPanel) {
                    if (centerComponent != null) mainPanel.remove(centerComponent); // Remove anything else from CENTER
                    mainPanel.add(signInButtonPanel, BorderLayout.CENTER);
                }
                // Set visibility
                signedInContentPanel.setVisible(false);
                signInButtonPanel.setVisible(true);
            }

            mainPanel.revalidate();
            mainPanel.repaint();
        });
    }


    private void handleSignIn(ActionEvent e) {
        LOG.info("Sign In button clicked.");
        if (AuthService.getInstance().isSignedIn()) return;
        signInButton.setEnabled(false);
        // statusLabel.setText("Status: Opening browser..."); // Status label might not be visible yet
        Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Sign In", "Opening browser for GitHub sign in...", NotificationType.INFORMATION), project); // Use notification
        AuthHelper.initiateSignInFlow(project);
        // Timer to re-enable button if auth fails/takes long
        Timer timer = new Timer(3000, ae -> {
            if (!AuthService.getInstance().isSignedIn() && !signInButton.isEnabled()) {
                signInButton.setEnabled(true);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Action handler for the "Resolve LSKs in Selection" button.
     * Performs the entire resolution process directly.
     */
    private void handleResolveLsk(ActionEvent event) {
        LOG.debug("Resolve LSK button clicked.");

        // --- Use project from this class instance ---
        final Project targetProject = this.project;
        if (targetProject == null) {
            LOG.error("Cannot resolve LSK: Project context is null for the tool window panel.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Error", "Cannot determine project context.", NotificationType.ERROR));
            return;
        }

        // --- Get Editor directly using FileEditorManager ---
        final Editor editor = FileEditorManager.getInstance(targetProject).getSelectedTextEditor();

        // 1. Perform Pre-checks (Editor Check First)
        if (editor == null) {
            LOG.warn("Resolve LSK cancelled: No active text editor found or focused.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Action Required", "Please focus an XML file in the editor.", NotificationType.WARNING), targetProject);
            return;
        }

        // --- Get PsiFile ---
        final Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(targetProject).getPsiFile(document);

// DataContext fallback for PsiFile (less critical now, but kept)
        if (psiFile == null) {
            LOG.warn("Could not get PsiFile from editor document, trying DataContext.");
            DataContext dataContext = null;
            try {
                dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
                if (dataContext == null) {
                    dataContext = DataManager.getInstance().getDataContextFromFocusAsync().blockingGet(100);
                }
            } catch (Exception ex) {
                LOG.warn("Failed to get DataContext: " + ex.getMessage(), ex);
            }
            if (dataContext != null) {
                psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
                if (psiFile != null) {
                    LOG.info("Obtained PsiFile via DataContext fallback.");
                }
            }
        }

        String relativePath = null; // Initialize relativePath variable
// First, ensure we actually *have* a psiFile after the checks above
        if (psiFile != null) {
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            String projectBasePath = targetProject.getBasePath();

            if (virtualFile != null && projectBasePath != null) {
                // Use FileUtil for robust relative path calculation, enforce '/' separator
                relativePath = FileUtil.getRelativePath(projectBasePath, virtualFile.getPath(), '/');

                if (relativePath != null) {
                    LOG.info("Relative path of selected file: " + relativePath);
                    int confIndex = relativePath.indexOf("conf/");

                    if (confIndex != -1) {
                        LOG.info("'conf/' found starting at index: " + confIndex);
                    } else {
                        LOG.info("'conf/' was not found in the path: " + relativePath);
                    }
                    java.util.List<String> pathDirectories = java.util.List.of(relativePath.split("/"));
                    final String currentModule = pathDirectories.get(1);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        currentModuleNameLabel.setText(currentModule);
                    });
                    setCurrentModuleName(currentModule);
                } else {
                    // Optionally clear the label if path cannot be determined
                    ApplicationManager.getApplication().invokeLater(() -> {
                        currentModuleNameLabel.setText("N/A\tClick the resolve button to fetch the Module name.");
                    });
                    LOG.warn("Could not determine relative path...");
                }
            } else {
                if (virtualFile == null) LOG.warn("Could not get VirtualFile from PsiFile to calculate relative path.");
                if (projectBasePath == null) LOG.warn("Could not get project base path to calculate relative path.");
            }
        } else {
            // This else block corresponds to the case where psiFile was still null after all attempts.
            // The subsequent check for psiFile == null will handle the notification.
            LOG.warn("Cannot calculate relative path because PsiFile is null.");
        }
        // --- Continue with other Pre-checks ---
        if (!AuthService.getInstance().isSignedIn()) {
            LOG.warn("Resolve LSK cancelled: User not signed in.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Sign In Required", "Please sign in before resolving LSKs.", NotificationType.WARNING), targetProject);
            return;
        }
        if (psiFile == null || !(psiFile instanceof XmlFile)) {
            LOG.warn("Resolve LSK cancelled: Active file is not an XML file or couldn't be determined.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Action Required", "Please ensure the active file is an XML file.", NotificationType.WARNING), targetProject);
            return;
        }
        if (!editor.getSelectionModel().hasSelection()) {
            LOG.warn("Resolve LSK cancelled: No text selected.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Action Required", "Please select the XML block containing placeholders.", NotificationType.WARNING), targetProject);
            return;
        }

        // 2. Determine Module Name
        String inputModuleName = moduleNameField.getText().trim();
        String moduleToUse = null;
        if (!inputModuleName.isEmpty()) {
            moduleToUse = inputModuleName;
            LOG.info("Using module name from input field: " + moduleToUse);
        } else {
            ;
            if (currentModuleName != null && !currentModuleName.isEmpty()) {
                moduleToUse = currentModuleName;
                LOG.info("Module name field empty, using project directory name as default: " + moduleToUse);
            } else {
                LOG.error("Module name is required but not provided in field and project name is unavailable.");
                Notifications.Bus.notify(new Notification(
                        PluginConstants.NOTIFICATION_GROUP_ID, "Module Name Required",
                        "Could not determine default module name. Please enter one in the 'Module Name' field.",
                        NotificationType.WARNING), targetProject);
                return;
            }
        }

        // 3. Get Selected Text and Auth Token
        final String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            LOG.warn("Resolve LSK cancelled: Selected text is empty.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Action Required", "Selected text is empty.", NotificationType.WARNING), targetProject);
            return;
        }
        final String authToken = AuthService.getInstance().getAuthToken();
        if (authToken == null) {
            LOG.error("Resolve LSK cancelled: Auth token is null despite being signed in.");
            Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Error", "Internal error: Authentication token missing.", NotificationType.ERROR), targetProject);
            return;
        }

        // 4. Call Backend Service (Background Thread)
        final String finalModuleToUse = moduleToUse;
        final PsiFile finalPsiFile = psiFile;
        Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "LSK Resolver", "Resolving placeholders...", NotificationType.INFORMATION), targetProject);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BackendApiService backendService = BackendApiService.getInstance();
                ApiResponse response = backendService.resolveLskSelection(finalModuleToUse, selectedText, authToken);

                // 5. Process Response and Update Editor (EDT)
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!targetProject.isDisposed() && !editor.isDisposed()) { // Check project/editor validity
                        if (response.getError() != null) {
                            LOG.warn("LSK resolution failed: " + response.getError());
                            Notifications.Bus.notify(new Notification(
                                    PluginConstants.NOTIFICATION_GROUP_ID, "Resolution Failed",
                                    "Backend Error: " + response.getError(), NotificationType.ERROR), targetProject);
                        } else if (response.getData() != null) {
                            LOG.info("LSK resolution successful. Updating editor.");
                            final String resolvedXml = response.getData();
                            final int startOffset = editor.getSelectionModel().getSelectionStart();
                            final int endOffset = editor.getSelectionModel().getSelectionEnd();

                            WriteCommandAction.runWriteCommandAction(targetProject, "Resolve LSK Placeholders", null, () -> {
                                if (document.isWritable()) {
                                    document.replaceString(startOffset, endOffset, resolvedXml);
                                } else {
                                    LOG.warn("Document not writable during write action.");
                                    Notifications.Bus.notify(new Notification(PluginConstants.NOTIFICATION_GROUP_ID, "Warning", "Could not write to document.", NotificationType.WARNING), targetProject);
                                }
                            }, finalPsiFile);

                            editor.getSelectionModel().removeSelection();
                            Notifications.Bus.notify(new Notification(
                                    PluginConstants.NOTIFICATION_GROUP_ID, "Resolution Successful",
                                    "Selected placeholders replaced.", NotificationType.INFORMATION), targetProject);
                        } else {
                            LOG.error("LSK resolution returned unexpected state: No error and no data.");
                            Notifications.Bus.notify(new Notification(
                                    PluginConstants.NOTIFICATION_GROUP_ID, "Error",
                                    "Internal error: Invalid response from backend.", NotificationType.ERROR), targetProject);
                        }
                    } else {
                        LOG.warn("Project or Editor disposed before LSK result could be processed.");
                    }
                }, ModalityState.defaultModalityState());

            } catch (IOException | IllegalArgumentException e) {
                LOG.error("Error calling LSK resolve API: " + e.getMessage(), e);
                ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(new Notification(
                        PluginConstants.NOTIFICATION_GROUP_ID, "Resolution Error",
                        "Failed to communicate with backend: " + e.getMessage(), NotificationType.ERROR), targetProject), ModalityState.defaultModalityState());
            } catch (Exception e) {
                LOG.error("Unexpected error during LSK resolution: " + e.getMessage(), e);
                ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(new Notification(
                        PluginConstants.NOTIFICATION_GROUP_ID, "Resolution Error",
                        "An unexpected error occurred: " + e.getMessage(), NotificationType.ERROR), targetProject), ModalityState.defaultModalityState());
            }
        });
    }


    public JPanel getMainPanel() {
        return mainPanel;
    }


    private void subscribeToAuthChanges() {
        if (connection == null) {
            connection = ApplicationManager.getApplication().getMessageBus().connect(this);
            LOG.debug("Subscribing ToolWindowPanel to AuthenticationListener topic.");
            connection.subscribe(AuthenticationListener.TOPIC, new AuthenticationListener() {
                @Override
                public void authenticationStateChanged() {
                    LOG.info("Received authenticationStateChanged event. Refreshing ToolWindow UI.");
                    updateUIState();
                }
            });
        }
    }
    @Override
    public void dispose() {
        LOG.debug("Disposing CatalystToolWindowPanel.");
        // MessageBus connection automatically disposed because we used connect(this)
    }
}
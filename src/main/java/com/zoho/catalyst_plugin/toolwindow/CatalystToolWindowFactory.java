package com.zoho.catalyst_plugin.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory class responsible for creating the UI content of the
 * Catalyst LSK Tool Window when the user clicks its icon.
 */
public class CatalystToolWindowFactory implements ToolWindowFactory, DumbAware {

    /**
     * Creates the tool window content.
     * This method is called by the IntelliJ Platform framework when the tool window is first opened.
     * @param project The current project.
     * @param toolWindow The tool window instance being created.
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 1. Create an instance of our custom UI panel class
        CatalystToolWindowPanel toolWindowPanel = new CatalystToolWindowPanel(project, toolWindow);

        // 2. Get the factory for creating tool window content elements
        ContentFactory contentFactory = ContentFactory.getInstance();

        // 3. Create a content element containing our main UI panel
        //    Parameters: component, displayName (tab title, empty for single tab), isPinnable
        Content content = contentFactory.createContent(toolWindowPanel.getMainPanel(), "", false);

        // 4. Add the content element to the tool window's manager
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * Determines if the tool window should be available for the given project.
     * Returning true makes it always available.
     */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    // Optional: Implement init(ToolWindow window) if needed for setup that
    // doesn't involve content creation (rarely needed).
    // @Override
    // public void init(@NotNull ToolWindow toolWindow) {}
}
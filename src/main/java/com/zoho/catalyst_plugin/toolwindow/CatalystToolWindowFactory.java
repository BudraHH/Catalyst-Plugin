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

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 1. Create an instance of our existing UI panel class
        CatalystToolWindowPanel toolWindowPanel = new CatalystToolWindowPanel(project, toolWindow);

        // 2. Get the factory for creating tool window content elements
        ContentFactory contentFactory = ContentFactory.getInstance();

        // 3. Create a content element containing our main UI panel
        Content content = contentFactory.createContent(toolWindowPanel.getMainPanel(), "", false);

        // 4. Add the content element to the tool window's manager
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true; // Make the tool window available for all projects
    }
}



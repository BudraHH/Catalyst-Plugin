package com.zoho.catalyst_plugin.listeners;

import com.intellij.util.messages.Topic;
import java.util.EventListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProjectInfoListener extends EventListener {
    Topic<ProjectInfoListener> TOPIC = Topic.create("Catalyst LSK Project Info Update", ProjectInfoListener.class);

    // Method to pass project-specific info
    void projectRootPathChanged(@NotNull Project project, @Nullable String rootPath);
}
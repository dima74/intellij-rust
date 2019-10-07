package org.rust.ide.console;

import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.function.Function;

// todo мб 90% этого класса можно удалить?
public class RsConsoleToolWindow {
  public static final Key<RunContentDescriptor> CONTENT_DESCRIPTOR = Key.create("CONTENT_DESCRIPTOR");

  public static final Function<Content, RunContentDescriptor>
    CONTENT_TO_DESCRIPTOR_FUNCTION = input -> input != null ? input.getUserData(CONTENT_DESCRIPTOR) : null;

  private final Project myProject;

  private boolean myInitialized = false;

  public RsConsoleToolWindow(Project project) {
    myProject = project;
  }

  public static RsConsoleToolWindow getInstance(@NotNull Project project) {
    return project.getComponent(RsConsoleToolWindow.class);
  }

  public List<RunContentDescriptor> getConsoleContentDescriptors() {
    return FluentIterable.from(Lists.newArrayList(getToolWindow(myProject).getContentManager().getContents()))
      .transform(CONTENT_TO_DESCRIPTOR_FUNCTION::apply).filter(
        Predicates.notNull()).toList();
  }


  public void init(final @NotNull ToolWindow toolWindow, final @NotNull RunContentDescriptor contentDescriptor) {
    setContent(toolWindow, contentDescriptor);

    if (!myInitialized) {
      doInit(toolWindow);
    }
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  private void doInit(@NotNull final ToolWindow toolWindow) {
    myInitialized = true;

    toolWindow.setToHideOnEmptyContent(true);

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        ToolWindow window = getToolWindow(myProject);
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible && toolWindow.getContentManager().getContentCount() == 0) {
            RsConsoleRunner runner = RsConsoleRunnerFactory.Companion.getInstance().createConsoleRunner(myProject, null);
            runner.run(true);
          }
        }
      }
    });
  }

  private static void setContent(ToolWindow toolWindow, RunContentDescriptor contentDescriptor) {
    toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");

    Content content = toolWindow.getContentManager().findContent(contentDescriptor.getDisplayName());
    if (content == null) {
      content = createContent(contentDescriptor);
      toolWindow.getContentManager().addContent(content);
    }
    else {
      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      resetContent(contentDescriptor, panel, content);
    }

    toolWindow.getContentManager().setSelectedContent(content);
  }

  public ToolWindow getToolWindow() {
    return getToolWindow(myProject);
  }

  public static ToolWindow getToolWindow(Project project) {
    return ToolWindowManager.getInstance(project).getToolWindow(RsConsoleToolWindowFactory.ID);
  }

  public void setContent(RunContentDescriptor contentDescriptor) {
    setContent(getToolWindow(myProject), contentDescriptor);
  }

  private static Content createContent(final @NotNull RunContentDescriptor contentDescriptor) {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, contentDescriptor.getDisplayName(), false);
    content.setCloseable(true);

    resetContent(contentDescriptor, panel, content);

    return content;
  }

  private static void resetContent(RunContentDescriptor contentDescriptor, SimpleToolWindowPanel panel, Content content) {
    RunContentDescriptor oldDescriptor =
      content.getDisposer() instanceof RunContentDescriptor ? (RunContentDescriptor)content.getDisposer() : null;
    if (oldDescriptor != null) Disposer.dispose(oldDescriptor);

    panel.setContent(contentDescriptor.getComponent());

    content.setComponent(panel);
    content.setDisposer(contentDescriptor);
    content.setPreferredFocusableComponent(contentDescriptor.getComponent());

    content.putUserData(CONTENT_DESCRIPTOR, contentDescriptor);
  }

  private static FocusListener createFocusListener(final ToolWindow toolWindow) {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus(toolWindow);
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    };
  }

  private static JComponent getComponentToFocus(ToolWindow window) {
    return window.getContentManager().getComponent();
  }


  public void activate(@NotNull Runnable runnable) {
    getToolWindow(myProject).activate(runnable);
  }

  @Nullable
  public RunContentDescriptor getSelectedContentDescriptor() {
    return CONTENT_TO_DESCRIPTOR_FUNCTION.apply(getToolWindow(myProject).getContentManager().getSelectedContent());
  }
}

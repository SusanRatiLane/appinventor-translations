// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.explorer.commands;

import static com.google.appinventor.client.Ode.MESSAGES;

import com.google.appinventor.client.DesignToolbar;
import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.editor.FileEditor;
import com.google.appinventor.client.editor.ProjectEditor;
import com.google.appinventor.client.explorer.project.Project;
import com.google.appinventor.client.output.OdeLog;
import com.google.appinventor.client.widgets.LabeledTextBox;
import com.google.appinventor.client.youngandroid.TextValidators;
import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidBlocksNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidSourceNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidTaskNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidPackageNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidProjectNode;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;

import java.util.HashSet;
import java.util.Set;

/**
 * A command that creates a new task.
 */
public final class AddTaskCommand extends ChainableCommand {


  private static final int MAX_TASK_COUNT = 1; // This has to be incremented.

  /**
   * Creates a new command for creating a new task
   */
  public AddTaskCommand() {
  }

  @Override
  public boolean willCallExecuteNextCommand() {
    return true;
  }

  @Override
  public void execute(ProjectNode node) {
    if (node instanceof YoungAndroidProjectNode) {
      new NewTaskDialog((YoungAndroidProjectNode) node).center();
    } else {
      executionFailedOrCanceled();
      throw new IllegalArgumentException("node must be a YoungAndroidProjectNode");
    }
  }

  /**
   * Dialog for getting the name for the new form.
   */
  private class NewTaskDialog extends DialogBox {
    // UI elements
    private final LabeledTextBox newNameTextBox;

    private final Set<String> otherContextNames;

    NewTaskDialog(final YoungAndroidProjectNode projectRootNode) {
      super(false, true);

      setStylePrimaryName("ode-DialogBox");
      setText(MESSAGES.newTaskTitle());
      VerticalPanel contentPanel = new VerticalPanel();

      final String prefix = "Task";
      final int prefixLength = prefix.length();
      int highIndex = 0;
      int taskCount = 0;
      // Collect the existing task names so we can prevent duplicate task names.
      otherContextNames = new HashSet<String>();

      for (ProjectNode source : projectRootNode.getAllSourceNodes()) {
        if (source instanceof YoungAndroidSourceNode) {
          String contextName = ((YoungAndroidSourceNode) source).getContextName();
          otherContextNames.add(contextName);
          if (source instanceof YoungAndroidTaskNode) {
            taskCount++;
            if (contextName.startsWith(prefix)) {
              try {
                highIndex = Math.max(highIndex, Integer.parseInt(contextName.substring(prefixLength)));
              } catch (NumberFormatException e) {
                continue;
              }
            }
          }

        }
      }

      String defaultTaskName = prefix + (highIndex + 1);

      newNameTextBox = new LabeledTextBox(MESSAGES.taskNameLabel());
      newNameTextBox.setText(defaultTaskName);
      newNameTextBox.getTextBox().addKeyUpHandler(new KeyUpHandler() {
        @Override
        public void onKeyUp(KeyUpEvent event) {
          int keyCode = event.getNativeKeyCode();
          if (keyCode == KeyCodes.KEY_ENTER) {
            handleOkClick(projectRootNode);
          } else if (keyCode == KeyCodes.KEY_ESCAPE) {
            hide();
            executionFailedOrCanceled();
          }
        }
      });
      contentPanel.add(newNameTextBox);

      String cancelText = MESSAGES.cancelButton();
      String okText = MESSAGES.okButton();

      // Keeps track of the total number of tasks.
      taskCount = taskCount + 1;
      if (taskCount > MAX_TASK_COUNT) {
        HorizontalPanel errorPanel = new HorizontalPanel();
        HTML tooManyTasksLabel = new HTML(MESSAGES.formCountErrorLabel());
        errorPanel.add(tooManyTasksLabel);
        errorPanel.setSize("100%", "24px");
        contentPanel.add(errorPanel);

        okText = MESSAGES.addScreenButton();
        cancelText = MESSAGES.cancelScreenButton();
      }

      Button cancelButton = new Button(cancelText);
      cancelButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          hide();
          executionFailedOrCanceled();
        }
      });
      Button okButton = new Button(okText);
      okButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          handleOkClick(projectRootNode);
        }
      });
      HorizontalPanel buttonPanel = new HorizontalPanel();
      buttonPanel.add(cancelButton);
      buttonPanel.add(okButton);
      buttonPanel.setSize("100%", "24px");
      contentPanel.add(buttonPanel);
      contentPanel.setSize("320px", "100%");

      add(contentPanel);
    }

    private void handleOkClick(YoungAndroidProjectNode projectRootNode) {
      String newTaskName = newNameTextBox.getText();
      if (validate(newTaskName)) {
        hide();
        addTaskAction(projectRootNode, newTaskName);
      } else {
        newNameTextBox.setFocus(true);
      }
    }

    private boolean validate(String newTaskName) {
      // Check that it meets the formatting requirements.
      if (!TextValidators.isValidIdentifier(newTaskName)) {
        Window.alert(MESSAGES.malformedTaskNameError());
        return false;
      }

      // Check that it's unique.
      if (otherContextNames.contains(newTaskName)) {
        Window.alert(MESSAGES.duplicateTaskNameError());
        return false;
      }

      return true;
    }

    /**
     * Adds a new task to the project.
     *
     * @param taskName the new task name
     */
    protected void addTaskAction(final YoungAndroidProjectNode projectRootNode,
                                 final String taskName) {
      final Ode ode = Ode.getInstance();
      final YoungAndroidPackageNode packageNode = projectRootNode.getPackageNode();
      String qualifiedTaskName = packageNode.getPackageName() + '.' + taskName;
      final String taskFileId = YoungAndroidTaskNode.getTaskFileId(qualifiedTaskName);
      final String blocksFileId = YoungAndroidBlocksNode.getBlocklyFileId(qualifiedTaskName);

      OdeAsyncCallback<Long> callback = new OdeAsyncCallback<Long>(
          // failure message
          MESSAGES.addTaskError()) {
        @Override
        public void onSuccess(Long modDate) {
          final Ode ode = Ode.getInstance();
          ode.updateModificationDate(projectRootNode.getProjectId(), modDate);

          // Add the new task and blocks nodes to the project
          final Project project = ode.getProjectManager().getProject(projectRootNode);
          project.addNode(packageNode, new YoungAndroidTaskNode(taskFileId));
          project.addNode(packageNode, new YoungAndroidBlocksNode(blocksFileId));

          // Add the task to the DesignToolbar and select the new task editor.
          // We need to do this once the task editor and blocks editor have been
          // added to the project editor (after the files are completely loaded).
          Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
              OdeLog.wlog("Adding to toolbar " + taskFileId);
              ProjectEditor projectEditor =
                  ode.getEditorManager().getOpenProjectEditor(project.getProjectId());
              FileEditor taskEditor = projectEditor.getFileEditor(taskFileId);
              FileEditor blocksEditor = projectEditor.getFileEditor(blocksFileId);
              if (taskEditor != null && blocksEditor != null && !ode.contextsLocked()) {
                DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
                long projectId = taskEditor.getProjectId();
                designToolbar.addTask(projectId, taskName, taskEditor,
                    blocksEditor);
                designToolbar.switchToContext(projectId, taskName, DesignToolbar.View.CONTEXT);
                executeNextCommand(projectRootNode);
              } else {
                // The task editor and/or blocks editor is still not there. Try again later.
                Scheduler.get().scheduleDeferred(this);
              }
            }
          });

        }

        @Override
        public void onFailure(Throwable caught) {
          super.onFailure(caught);
          executionFailedOrCanceled();
        }
      };

      // Create the new task on the backend. The backend will create the task (.tsk) and blocks
      // (.blk) files.
      ode.getProjectService().addFile(projectRootNode.getProjectId(), taskFileId, callback);
    }

    @Override
    public void show() {
      super.show();

      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          newNameTextBox.setFocus(true);
        }
      });
    }
  }
}

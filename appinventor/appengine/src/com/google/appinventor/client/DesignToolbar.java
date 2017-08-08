// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client;

import com.google.appinventor.client.editor.FileEditor;
import com.google.appinventor.client.editor.ProjectEditor;
import com.google.appinventor.client.editor.youngandroid.*;
import com.google.appinventor.client.explorer.commands.AddFormCommand;
import com.google.appinventor.client.explorer.commands.AddTaskCommand;
import com.google.appinventor.client.explorer.commands.ChainableCommand;
import com.google.appinventor.client.explorer.commands.DeleteFileCommand;

import com.google.appinventor.client.output.OdeLog;

import com.google.appinventor.client.tracking.Tracking;

import com.google.appinventor.client.widgets.DropDownButton.DropDownItem;

import com.google.appinventor.client.widgets.Toolbar;

import com.google.appinventor.common.version.AppInventorFeatures;

import com.google.appinventor.shared.rpc.project.ProjectRootNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidSourceNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.Scheduler;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.google.appinventor.client.Ode.MESSAGES;

/**
 * The design toolbar houses command buttons in the Young Android Design
 * tab (Context Editor and Blocks Editor).
 *
 */
public class DesignToolbar extends Toolbar {

  private boolean isReadOnly;   // If the UI is in read only mode

  /*
   * A Context groups together the context editor and blocks editor for an
   * application screen. Name is the name of the context (form or task) displayed
   * in the screens pull-down.
   */
  public static class Context {
    public final String contextName;
    public final YaContextEditor contextEditor;
    public final YaBlocksEditor blocksEditor;

    public Context(String name, FileEditor contextEditor, FileEditor blocksEditor) {
      this.contextName = name;
      this.contextEditor = (YaContextEditor) contextEditor;
      this.blocksEditor = (YaBlocksEditor) blocksEditor;
    }
  }

  /*
   * A project as represented in the DesignToolbar. Each project has a name
   * (as displayed in the DesignToolbar on the left), a set of named contexts,
   * and an indication of which context is currently being edited.
   */
  public static class DesignProject {
    public final String name;
    public final Map<String, Context> screens; // screen name -> Screen
    public final Map<String, Context> tasks; // task name -> Task
    public String currentContext; // name of currently displayed screen/task
    public String lastScreen; // name of the last opened screen. May be equal to currentContext.

    public DesignProject(String name, long projectId) {
      this.name = name;
      screens = Maps.newHashMap();
      tasks = Maps.newHashMap();
      // Screen1 is initial context by default
      currentContext = YoungAndroidSourceNode.SCREEN1_FORM_NAME;
      lastScreen = currentContext;
      BlocklyPanel.setCurrentContext(projectId + "_" + currentContext);
      // Let BlocklyPanel know which context to send Yail for
      BlocklyPanel.setCurrentForm(projectId + "_" + currentContext);
    }

    public Context getContext(String contextName) {
      Context context = screens.get(contextName);
      if (context == null) {
        context = tasks.get(contextName);
      }
      return context;
    }

    public List<Context> getTasks() {
      List<Context> tasks = new ArrayList<Context>();
      for (Context task : this.tasks.values()) {
        tasks.add(task);
      }
      return tasks;
    }

    public Context getLastScreen() {
      return screens.get(lastScreen);
    }

    // Returns true if we added the screen (it didn't previously exist), false otherwise.
    public boolean addScreen(String name, FileEditor formEditor, FileEditor blocksEditor) {
      if (!screens.containsKey(name) && !tasks.containsKey(name)) {
        screens.put(name, new Context(name, formEditor, blocksEditor));
        return true;
      } else {
        return false;
      }
    }

    public void removeScreen(String name) {
      screens.remove(name);
    }

    public boolean addTask(String name, FileEditor taskEditor, FileEditor blocksEditor) {
      if (!tasks.containsKey(name) && !screens.containsKey(name)) {
        tasks.put(name, new Context(name, taskEditor, blocksEditor));
        return true;
      } else {
        return false;
      }
    }

    public void removeTask(String name) {
      tasks.remove(name);
    }

    public void setCurrentContext(String name) {
      currentContext = name;
      if (screens.containsKey(currentContext)) {
        this.lastScreen = currentContext;
      }
    }

    public void removeContext(String name) {
      if (screens.containsKey(name)) {
        this.removeScreen(name);
      }
      if (tasks.containsKey(name)) {
        this.removeTask(name);
      }
    }

  }

  private static final String WIDGET_NAME_TUTORIAL_TOGGLE = "TutorialToggle";
  private static final String WIDGET_NAME_ADDFORM = "AddForm";
  private static final String WIDGET_NAME_ADDTASK = "AddTask";
  private static final String WIDGET_NAME_REMOVECONTEXT = "RemoveContext";

  private static final String WIDGET_NAME_CONTEXTS_DROPDOWN = "ContextsDropdown";
  private static final String WIDGET_NAME_SWITCH_TO_BLOCKS_EDITOR = "SwitchToBlocksEditor";
  private static final String WIDGET_NAME_SWITCH_TO_CONTEXT_EDITOR = "SwitchToContextEditor";

  // Switch language
  private static final String WIDGET_NAME_SWITCH_LANGUAGE = "Language";
  private static final String WIDGET_NAME_SWITCH_LANGUAGE_ENGLISH = "English";
  private static final String WIDGET_NAME_SWITCH_LANGUAGE_CHINESE_CN = "Simplified Chinese";
  private static final String WIDGET_NAME_SWITCH_LANGUAGE_SPANISH_ES = "Spanish-Spain";
  //private static final String WIDGET_NAME_SWITCH_LANGUAGE_GERMAN = "German";
  //private static final String WIDGET_NAME_SWITCH_LANGUAGE_VIETNAMESE = "Vietnamese";

  // Enum for type of view showing in the design tab
  public enum View {
    CONTEXT,   // Context editor view
    BLOCKS  // Blocks editor view
  }
  public View currentView = View.CONTEXT;

  public Label projectNameLabel;

  // Project currently displayed in designer
  private DesignProject currentProject;

  // Map of project id to project info for all projects we've ever shown
  // in the Designer in this session.
  public Map<Long, DesignProject> projectMap = Maps.newHashMap();

  // Stack of screens switched to from the Companion
  // We implement screen switching in the Companion by having it tell us
  // to switch screens. We then load into the companion the new Screen
  // We save where we were because the companion can have us return from
  // a screen. If we switch projects in the browser UI, we clear this
  // list of screens as we are effectively running a different application
  // on the device.
  public static LinkedList<String> pushedScreens = Lists.newLinkedList();

  /**
   * Initializes and assembles all commands into buttons in the toolbar.
   */
  public DesignToolbar() {
    super();

    isReadOnly = Ode.getInstance().isReadOnly();

    projectNameLabel = new Label();
    projectNameLabel.setStyleName("ya-ProjectName");
    HorizontalPanel toolbar = (HorizontalPanel) getWidget();
    toolbar.insert(projectNameLabel, 0);

    // width of palette minus cellspacing/border of buttons
    toolbar.setCellWidth(projectNameLabel, "222px");

    addButton(new ToolbarItem(WIDGET_NAME_TUTORIAL_TOGGLE,
            MESSAGES.toggleTutorialButton(), new ToogleTutorialAction()));
    setButtonVisible(WIDGET_NAME_TUTORIAL_TOGGLE, false); // Don't show unless needed

    List<DropDownItem> contextItems = Lists.newArrayList();
    addDropDownButton(WIDGET_NAME_CONTEXTS_DROPDOWN, MESSAGES.contextsButton(), contextItems);

    if (AppInventorFeatures.allowMultiScreenApplications() && !isReadOnly) {
      addButton(new ToolbarItem(WIDGET_NAME_ADDFORM, MESSAGES.addFormButton(),
              new AddFormAction()));
    }
    if (AppInventorFeatures.allowTasks() && !isReadOnly) {
      addButton(new ToolbarItem(WIDGET_NAME_ADDTASK, MESSAGES.addTaskButton(),
              new AddTaskAction()));
    }
    if((AppInventorFeatures.allowMultiScreenApplications() || AppInventorFeatures.allowTasks()) && !isReadOnly) {
      addButton(new ToolbarItem(WIDGET_NAME_REMOVECONTEXT, MESSAGES.removeButton(),
              new RemoveContextAction()));

    }

    addButton(new ToolbarItem(WIDGET_NAME_SWITCH_TO_CONTEXT_EDITOR,
        MESSAGES.switchToContextEditorButton(), new SwitchToContextEditorAction()), true);
    addButton(new ToolbarItem(WIDGET_NAME_SWITCH_TO_BLOCKS_EDITOR,
        MESSAGES.switchToBlocksEditorButton(), new SwitchToBlocksEditorAction()), true);

    // Gray out the Designer button and enable the blocks button
    toggleEditor(false);
    Ode.getInstance().getTopToolbar().updateFileMenuButtons(0);
  }

  private class ToogleTutorialAction implements Command {
    @Override
    public void execute() {
      Ode ode = Ode.getInstance();
      boolean visible = ode.isTutorialVisible();
      if (visible) {
        ode.setTutorialVisible(false);
      } else {
        ode.setTutorialVisible(true);
      }
    }
  }

  private class AddFormAction implements Command {
    @Override
    public void execute() {
      Ode ode = Ode.getInstance();
      if (ode.contextsLocked()) {
        return;                 // Don't permit this if we are locked out (saving files)
      }
      final ProjectRootNode projectRootNode = ode.getCurrentYoungAndroidProjectRootNode();
      if (projectRootNode != null) {
        Runnable doSwitch = new Runnable() {
            @Override
            public void run() {
              ChainableCommand cmd = new AddFormCommand();
              cmd.startExecuteChain(Tracking.PROJECT_ACTION_ADDFORM_YA, projectRootNode);
            }
          };
        // take a screenshot of the current blocks if we are in the blocks editor
        if (currentView == View.BLOCKS) {
          Ode.getInstance().screenShotMaybe(doSwitch, false);
        } else {
          doSwitch.run();
        }
      }
    }
  }

  private class AddTaskAction implements Command {
    @Override
    public void execute() {
      Ode ode = Ode.getInstance();
      if (ode.contextsLocked()) {
        return;
      }
      final ProjectRootNode projectRootNode = ode.getCurrentYoungAndroidProjectRootNode();
      if (projectRootNode != null) {
        Runnable doSwitch = new Runnable() {
          @Override
          public void run() {
            ChainableCommand cmd = new AddTaskCommand();
            cmd.startExecuteChain(Tracking.PROJECT_ACTION_ADDTASK_YA, projectRootNode);
          }
        };
        // take a screenshot of the current blocks if we are in the blocks editor
        if (currentView == View.BLOCKS) {
          Ode.getInstance().screenShotMaybe(doSwitch, false);
        } else {
          doSwitch.run();
        }
      }
    }
  }

  private class RemoveContextAction implements Command {
    @Override
    public void execute() {
      Ode ode = Ode.getInstance();
      if (ode.contextsLocked()) {
        return;                 // Don't permit this if we are locked out (saving files)
      }
      YoungAndroidSourceNode sourceNode = ode.getCurrentYoungAndroidSourceNode();
      FileEditor fileEditor = ode.getCurrentFileEditor();
      if (sourceNode != null && !sourceNode.isScreen1()) {
        // DeleteFileCommand handles the whole operation, including displaying the confirmation
        // message dialog, closing the context editor and the blocks editor,
        // deleting the files in the server's storage, and deleting the
        // corresponding client-side nodes (which will ultimately trigger the
        // screen deletion in the DesignToolbar).
        String confirmMessage = "";
        String trackingAction = "";
        boolean isForm = false;
        boolean isTask = false;
        if (fileEditor instanceof YaContextEditor) {
          if (fileEditor instanceof YaFormEditor) {
            isForm = true;
          } else if (fileEditor instanceof YaTaskEditor) {
            isTask = true;
          }
        } else if (fileEditor instanceof YaBlocksEditor) {
          YaBlocksEditor blocksEditor = (YaBlocksEditor) fileEditor;
          if (blocksEditor.isFormBlocksEditor()) {
            isForm = true;
          }
          else if (blocksEditor.isTaskBlocksEditor()) {
            isTask = true;
          }
        }
        if (isForm) {
          confirmMessage = MESSAGES.reallyDeleteForm(sourceNode.getContextName());
          trackingAction = Tracking.PROJECT_ACTION_REMOVEFORM_YA;
        } else if (isTask) {
          confirmMessage = MESSAGES.reallyDeleteTask(sourceNode.getContextName());
          trackingAction = Tracking.PROJECT_ACTION_REMOVETASK_YA;
        }
        if (isForm || isTask) {
          final String deleteConfirmationMessage = confirmMessage;
          ChainableCommand cmd = new DeleteFileCommand() {
            @Override
            protected boolean deleteConfirmation() {
              return Window.confirm(deleteConfirmationMessage);
            }
          };
          cmd.startExecuteChain(trackingAction, sourceNode);
        }
      }
    }
  }

  private class SwitchContextAction implements Command {
    private final long projectId;
    private final String name;  // screen name

    public SwitchContextAction(long projectId, String contextName) {
      this.projectId = projectId;
      this.name = contextName;
    }

    @Override
    public void execute() {
      // If we are in the blocks view, we should take a screenshot
      // of the blocks as we swtich to a different screen
      if (currentView == View.BLOCKS) {
        Ode.getInstance().screenShotMaybe(new Runnable() {
            @Override
            public void run() {
              doSwitchContext(projectId, name, currentView);
            }
          }, false);
      } else {
        doSwitchContext(projectId, name, currentView);
      }
    }
  }

  private void doSwitchContext(final long projectId, final String contextName, final View view) {
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          if (Ode.getInstance().contextsLocked()) { // Wait until I/O complete
            Scheduler.get().scheduleDeferred(this);
          } else {
            doSwitchContext1(projectId, contextName, view);
          }
        }
      });
  }

  private void doSwitchContext1(long projectId, String contextName, View view) {
    if (!projectMap.containsKey(projectId)) {
      OdeLog.wlog("DesignToolbar: no project with id " + projectId
          + ". Ignoring SwitchScreenAction.execute().");
      return;
    }
    DesignProject project = projectMap.get(projectId);
    if (currentProject != project) {
      // need to switch projects first. this will not switch contexts.
      if (!switchToProject(projectId, project.name)) {
        return;
      }
    }
    String newContextName = contextName;
    boolean isScreen = currentProject.screens.containsKey(newContextName);
    boolean isTask = currentProject.tasks.containsKey(newContextName);
    if (!isScreen && !isTask) {
      // Can't find the requested context in this project. This shouldn't happen, but if it does
      // for some reason, try switching to Screen1 instead.
      OdeLog.wlog("Trying to switch to non-existent context " + newContextName +
          " in project " + currentProject.name + ". Trying Screen1 instead.");
      if (currentProject.screens.containsKey(YoungAndroidSourceNode.SCREEN1_FORM_NAME)) {
        newContextName = YoungAndroidSourceNode.SCREEN1_FORM_NAME;
        isScreen = true;
      } else {
        // something went seriously wrong!
        ErrorReporter.reportError("Something is wrong. Can't find Screen1 for project "
            + currentProject.name);
        return;
      }
    }
    currentView = view;
    Context context = null;
    if (isScreen) {
      context = currentProject.screens.get(newContextName);
    } else if (isTask) {
      context = currentProject.tasks.get(newContextName);
    }
    ProjectEditor projectEditor = context.contextEditor.getProjectEditor();
    currentProject.setCurrentContext(newContextName);
    setDropDownButtonCaption(WIDGET_NAME_CONTEXTS_DROPDOWN, newContextName);
    if (currentView == View.CONTEXT) {
      projectEditor.selectFileEditor(context.contextEditor);
      toggleEditor(false);
      Ode.getInstance().getTopToolbar().updateFileMenuButtons(1);
    } else {  // must be View.BLOCKS
      projectEditor.selectFileEditor(context.blocksEditor);
      toggleEditor(true);
      Ode.getInstance().getTopToolbar().updateFileMenuButtons(1);
    }
    // Inform the Blockly Panel which project/context we are working on
    OdeLog.log("Setting currentContext to " + newContextName);
    BlocklyPanel.setCurrentContext(projectId + "_" + newContextName);
    if (isScreen) {
      BlocklyPanel.setCurrentForm(projectId + "_" + newContextName);
    }
    context.blocksEditor.makeActiveWorkspace();
  }

  private class SwitchToBlocksEditorAction implements Command {
    @Override
    public void execute() {
      if (currentProject == null) {
        OdeLog.wlog("DesignToolbar.currentProject is null. "
            + "Ignoring SwitchToBlocksEditorAction.execute().");
        return;
      }
      if (currentView != View.BLOCKS) {
        long projectId = Ode.getInstance().getCurrentYoungAndroidProjectRootNode().getProjectId();
        switchToContext(projectId, currentProject.currentContext, View.BLOCKS);
        toggleEditor(true);       // Gray out the blocks button and enable the designer button
        Ode.getInstance().getTopToolbar().updateFileMenuButtons(1);
      }
    }
  }

  private class SwitchToContextEditorAction implements Command {
    @Override
    public void execute() {
      if (currentProject == null) {
        OdeLog.wlog("DesignToolbar.currentProject is null. "
            + "Ignoring SwitchToContextEditorAction.execute().");
        return;
      }
      if (currentView != View.CONTEXT) {
        // We are leaving a blocks editor, so take a screenshot
        Ode.getInstance().screenShotMaybe(new Runnable() {
          @Override
          public void run() {
            long projectId = Ode.getInstance().getCurrentYoungAndroidProjectRootNode().getProjectId();
            switchToContext(projectId, currentProject.currentContext, View.CONTEXT);
            toggleEditor(false);      // Gray out the Designer button and enable the blocks button
            Ode.getInstance().getTopToolbar().updateFileMenuButtons(1);
          }
        }, false);
      }
    }
  }

  public void addProject(long projectId, String projectName) {
    if (!projectMap.containsKey(projectId)) {
      projectMap.put(projectId, new DesignProject(projectName, projectId));
      OdeLog.log("DesignToolbar added project " + projectName + " with id " + projectId);
    } else {
      OdeLog.wlog("DesignToolbar ignoring addProject for existing project " + projectName
          + " with id " + projectId);
    }
  }

  // Switch to an existing project. Note that this does not switch screens.
  // TODO(sharon): it might be better to throw an exception if the
  // project doesn't exist.
  private boolean switchToProject(long projectId, String projectName) {
    if (projectMap.containsKey(projectId)) {
      DesignProject project = projectMap.get(projectId);
      if (project == currentProject) {
        OdeLog.wlog("DesignToolbar: ignoring call to switchToProject for current project");
        return true;
      }
      pushedScreens.clear();    // Effectively switching applications clear stack of screens
      clearDropDownMenu(WIDGET_NAME_CONTEXTS_DROPDOWN);
      OdeLog.log("DesignToolbar: switching to existing project " + projectName + " with id "
          + projectId);
      currentProject = projectMap.get(projectId);
      // TODO(sharon): add contexts to drop-down menu in the right order
      for (Context screen : currentProject.screens.values()) {
        addDropDownButtonItem(WIDGET_NAME_CONTEXTS_DROPDOWN, new DropDownItem(screen.contextName,
            screen.contextName, new SwitchContextAction(projectId, screen.contextName)));
      }
      for (Context task : currentProject.tasks.values()) {
        addDropDownButtonItem(WIDGET_NAME_CONTEXTS_DROPDOWN, new DropDownItem(task.contextName,
            task.contextName, new SwitchContextAction(projectId, task.contextName)));
      }
      projectNameLabel.setText(projectName);
    } else {
      ErrorReporter.reportError("Design toolbar doesn't know about project " + projectName +
          " with id " + projectId);
      OdeLog.wlog("Design toolbar doesn't know about project " + projectName + " with id "
          + projectId);
      return false;
    }
    return true;
  }

  /*
   * Add a screen name to the drop-down for the project with id projectId.
   * name is the form name, formEditor is the file editor for the form UI,
   * and blocksEditor is the file editor for the form's blocks.
   */
  public void addScreen(long projectId, String name, FileEditor formEditor,
      FileEditor blocksEditor) {
    if (!projectMap.containsKey(projectId)) {
      OdeLog.wlog("DesignToolbar can't find project " + name + " with id " + projectId
          + ". Ignoring addScreen().");
      return;
    }
    DesignProject project = projectMap.get(projectId);
    if (project.addScreen(name, formEditor, blocksEditor)) {
      if (currentProject == project) {
        addDropDownButtonItem(WIDGET_NAME_CONTEXTS_DROPDOWN, new DropDownItem(name,
            name, new SwitchContextAction(projectId, name)));
      }
    }
  }

  /*
   * Add a task name to the drop-down for the project with id projectId.
   * name is the task name, taskEditor is the file editor for the task,
   * and blocksEditor is the file editor for the tasks's blocks.
   */
  public void addTask(long projectId, String name, FileEditor taskEditor,
                      FileEditor blocksEditor) {
    if (!projectMap.containsKey(projectId)) {
      OdeLog.wlog("DesignToolbar can't find project " + name + " with id " + projectId
          + ". Ignoring addTask().");
      return;
    }
    DesignProject project = projectMap.get(projectId);
    if (project.addTask(name, taskEditor, blocksEditor)) {
      if (currentProject == project) {
        addDropDownButtonItem(WIDGET_NAME_CONTEXTS_DROPDOWN, new DropDownItem(name,
            name, new SwitchContextAction(projectId, name))); //TODO:
      }
    }
  }

/*
 * PushScreen -- Static method called by Blockly when the Companion requests
 * That we switch to a new screen. We keep track of the Screen we were on
 * and push that onto a stack of Screens which we pop when requested by the
 * Companion.
 */
  public static boolean pushScreen(String screenName) {
    DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
    long projectId = Ode.getInstance().getCurrentYoungAndroidProjectId();
    String lastScreen = designToolbar.currentProject.lastScreen; // We may not be on a Screen
    if (!designToolbar.currentProject.screens.containsKey(screenName)) // No such screen -- can happen
      return false;                                                    // because screen is user entered here.
    pushedScreens.addFirst(lastScreen);
    designToolbar.doSwitchContext(projectId, screenName, View.BLOCKS);
    return true;
  }

  public static void popScreen() {
    DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
    long projectId = Ode.getInstance().getCurrentYoungAndroidProjectId();
    String newScreen;
    if (pushedScreens.isEmpty()) {
      return;                   // Nothing to do really
    }
    newScreen = pushedScreens.removeFirst();
    designToolbar.doSwitchContext(projectId, newScreen, View.BLOCKS);
  }

  // Called from Javascript when Companion is disconnected
  public static void clearScreens() {
    pushedScreens.clear();
  }

  /*
   * Switch to context name in project projectId. Also switches projects if
   * necessary.
   */
  public void switchToContext(long projectId, String contextName, View view) {
    doSwitchContext(projectId, contextName, view);
  }

  /*
   * Remove context name (if it exists) from project projectId
   */
  public void removeContext(long projectId, String name) {
    if (!projectMap.containsKey(projectId)) {
      OdeLog.wlog("DesignToolbar can't find project " + name + " with id " + projectId
          + " Ignoring removeContext().");
      return;
    }
    OdeLog.log("DesignToolbar: got removeContext for project " + projectId
        + ", context " + name);
    DesignProject project = projectMap.get(projectId);
    if (!project.screens.containsKey(name)) {
      // already removed this screen
      return;
    }
    if (currentProject == project) {
      // if removing current context, choose a new context to show
      if (currentProject.currentContext.equals(name)) {
        // TODO(sharon): maybe make a better choice than screen1, but for now
        // switch to screen1 because we know it is always there
        switchToContext(projectId, YoungAndroidSourceNode.SCREEN1_FORM_NAME, View.CONTEXT);
      }
      removeDropDownButtonItem(WIDGET_NAME_CONTEXTS_DROPDOWN, name);
    }
    project.removeContext(name);
  }

  private void toggleEditor(boolean blocks) {
    setButtonEnabled(WIDGET_NAME_SWITCH_TO_BLOCKS_EDITOR, !blocks);
    setButtonEnabled(WIDGET_NAME_SWITCH_TO_CONTEXT_EDITOR, blocks);

    if ((AppInventorFeatures.allowMultiScreenApplications() || AppInventorFeatures.allowTasks()) && !isReadOnly) {
      if (getCurrentProject() == null || getCurrentProject().currentContext == "Screen1") {
        setButtonEnabled(WIDGET_NAME_REMOVECONTEXT, false);
      } else {
        setButtonEnabled(WIDGET_NAME_REMOVECONTEXT, true);
      }
    }
  }

  public DesignProject getCurrentProject() {
    return currentProject;
  }

  public View getCurrentView() {
    return currentView;
  }

  public void setTutorialToggleVisible(boolean value) {
    if (value) {
      setButtonVisible(WIDGET_NAME_TUTORIAL_TOGGLE, true);
    } else {
      setButtonVisible(WIDGET_NAME_TUTORIAL_TOGGLE, false);
    }
  }

}

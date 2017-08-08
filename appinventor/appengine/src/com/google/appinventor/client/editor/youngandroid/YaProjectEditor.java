// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.youngandroid;

import static com.google.appinventor.client.Ode.MESSAGES;

import com.google.appinventor.client.DesignToolbar;
import com.google.appinventor.client.ErrorReporter;
import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.boxes.AssetListBox;
import com.google.appinventor.client.editor.FileEditor;
import com.google.appinventor.client.editor.ProjectEditor;
import com.google.appinventor.client.editor.ProjectEditorFactory;
import com.google.appinventor.client.editor.simple.SimpleComponentDatabase;
import com.google.appinventor.client.explorer.project.ComponentDatabaseChangeListener;
import com.google.appinventor.client.explorer.project.Project;
import com.google.appinventor.client.explorer.project.ProjectChangeListener;
import com.google.appinventor.client.output.OdeLog;
import com.google.appinventor.client.properties.json.ClientJsonParser;
import com.google.appinventor.common.utils.StringUtils;
import com.google.appinventor.shared.properties.json.JSONArray;
import com.google.appinventor.shared.properties.json.JSONObject;
import com.google.appinventor.shared.properties.json.JSONValue;
import com.google.appinventor.shared.rpc.project.ChecksumedFileException;
import com.google.appinventor.shared.rpc.project.ChecksumedLoadFile;
import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.appinventor.shared.rpc.project.ProjectRootNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidBlocksNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidComponentsFolder;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidFormNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidProjectNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidSourceNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidTaskNode;
import com.google.appinventor.shared.storage.StorageUtil;
import com.google.appinventor.shared.youngandroid.YoungAndroidSourceAnalyzer;
import com.google.common.collect.Maps;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project editor for Young Android projects. Each instance corresponds to
 * one project that has been opened in this App Inventor session. 
 * Also responsible for managing screens list for this project in 
 * the DesignToolbar.
 *
 * @author lizlooney@google.com (Liz Looney)
 * @author sharon@google.com (Sharon Perl) - added logic for screens in  
 *   DesignToolbar
 */
public final class YaProjectEditor extends ProjectEditor implements ProjectChangeListener, ComponentDatabaseChangeListener{
  
  // FileEditors in a YA project come in sets. Every context in the project has
  // a YaContextEditor for editing the Components, and a YaBlocksEditor for editing the
  // blocks representation of the program logic. Some day it may also have an 
  // editor for the textual representation of the program logic.
  private class EditorSet {
    YaContextEditor contextEditor = null;
    YaBlocksEditor blocksEditor = null;
  }

  // Maps context name -> editors for this context
  private final HashMap<String, EditorSet> editorMap = Maps.newHashMap();

  // Last Screen Context
  private EditorSet lastScreen;

  // List of External Components
  private final List<String> externalComponents = new ArrayList<String>();

  // Mapping of package names to extensions defined by the package (n > 1)
  private final Map<String, Set<String>> externalCollections = new HashMap<>();

  // Number of external component descriptors loaded since there is no longer a 1-1 correspondence
  private volatile int numExternalComponentsLoaded = 0;

  // List of ComponentDatabaseChangeListeners
  private final List<ComponentDatabaseChangeListener> componentDatabaseChangeListeners = new ArrayList<ComponentDatabaseChangeListener>();

  //State variables to help determine whether we are ready to load Project
  private boolean externalComponentsLoaded = false;

  // Database of component type descriptions
  private final SimpleComponentDatabase COMPONENT_DATABASE;

  // State variables to help determine whether we are ready to show Screen1  
  // Automatically select the Screen1 form editor when we have finished loading
  // both the form and blocks editors for Screen1 and we have added the 
  // screen to the DesignToolbar. Since the loading happens asynchronously,
  // there are multiple points when we may be ready to show the screen, and
  // we shouldn't try to show it before everything is ready.
  private boolean screen1FormLoaded = false;
  private boolean screen1BlocksLoaded = false;
  private boolean screen1Added = false;
  
  /**
   * Returns a project editor factory for {@code YaProjectEditor}s.
   *
   * @return a project editor factory for {@code YaProjectEditor}s.
   */
  public static ProjectEditorFactory getFactory() {
    return new ProjectEditorFactory() {
      @Override
      public ProjectEditor createProjectEditor(ProjectRootNode projectRootNode) {
        return new YaProjectEditor(projectRootNode);
      }
    };
  }

  public YaProjectEditor(ProjectRootNode projectRootNode) {
    super(projectRootNode);
    project.addProjectChangeListener(this);
    COMPONENT_DATABASE = SimpleComponentDatabase.getInstance(projectId);
  }

  private void loadBlocksEditor(String contextNamePassedIn) {

    final String contextName = contextNamePassedIn;
    final YaBlocksEditor newBlocksEditor = editorMap.get(contextName).blocksEditor;
    newBlocksEditor.loadFile(new Command() {
        @Override
        public void execute() {
          YaBlocksEditor newBlocksEditor = editorMap.get(contextName).blocksEditor;
          int pos = Collections.binarySearch(fileIds, newBlocksEditor.getFileId(),
              getFileIdComparator());
          if (pos < 0) {
            pos = -pos - 1;
          }
          insertFileEditor(newBlocksEditor, pos);
          if (isScreen1(contextName)) {
            screen1BlocksLoaded = true;
            if (readyToShowScreen1()) {
              OdeLog.log("YaProjectEditor.addBlocksEditor.loadFile.execute: switching to context "
                  + contextName + " for project " + newBlocksEditor.getProjectId());
              Ode.getInstance().getDesignToolbar().switchToContext(newBlocksEditor.getProjectId(),
                  contextName, DesignToolbar.View.CONTEXT);
            }
          }
        }
      });

  }

  /**
   * Project process is completed before loadProject is started!
   * Currently Project process loads all External Components into Component Database
   */
  @Override
  public void processProject() {
    resetExternalComponents();
    loadExternalComponents();
    callLoadProject();
  }

  // Note: When we add the blocks editors in the loop below we do not actually
  // have them load the blocks file. Instead we trigger the load of a blocks file
  // in the callback for the loading of its associated forms file. This is important
  // because we have to ensure that the component type data is available when the
  // blocks are loaded!

  private void loadProject() {
    // add form and task editors first, then blocks editors because the blocks editors
    // need access to their corresponding form editors to set up properly
    for (ProjectNode source : projectRootNode.getAllSourceNodes()) {
      if (source instanceof YoungAndroidFormNode) {
        addFormEditor((YoungAndroidFormNode) source);
      } else if (source instanceof YoungAndroidTaskNode) {
        addTaskEditor((YoungAndroidTaskNode) source);
      }
    }
    for (ProjectNode source : projectRootNode.getAllSourceNodes()) {
      if (source instanceof YoungAndroidBlocksNode) {
        addBlocksEditor((YoungAndroidBlocksNode) source);
      }
    }
    // Add the screens to the design toolbar, along with their associated editors
    DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
    for (String contextName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(contextName);
      if (editors.contextEditor != null && editors.blocksEditor != null) {
        if (editors.contextEditor instanceof YaFormEditor) {
          designToolbar.addScreen(projectRootNode.getProjectId(), contextName, editors.contextEditor,
              editors.blocksEditor);
          if (isScreen1(contextName)) {
            screen1Added = true;
            if (readyToShowScreen1()) {  // probably not yet but who knows?
              OdeLog.log("YaProjectEditor.loadProject: switching to context " + contextName
                  + " for project " + projectRootNode.getProjectId());
              Ode.getInstance().getDesignToolbar().switchToContext(projectRootNode.getProjectId(),
                  contextName, DesignToolbar.View.CONTEXT);
            }
          }
        } else if (editors.contextEditor instanceof YaTaskEditor) {
          designToolbar.addTask(projectRootNode.getProjectId(), contextName, editors.contextEditor, editors.blocksEditor);
        }
      } else if (editors.contextEditor == null) {
        OdeLog.wlog("Missing context editor for " + contextName);
      } else {
        OdeLog.wlog("Missing blocks editor for " + contextName);
      }
    }
  }
  
  @Override
  protected void onShow() {
    OdeLog.log("YaProjectEditor got onShow() for project " + projectId);
    
    AssetListBox.getAssetListBox().getAssetList().refreshAssetList(projectId);
    
    DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
    FileEditor selectedFileEditor = getSelectedFileEditor();
    if (selectedFileEditor != null) {
      if (selectedFileEditor instanceof YaContextEditor) {
        YaContextEditor contextEditor = (YaContextEditor) selectedFileEditor;
        designToolbar.switchToContext(projectId, contextEditor.getContext().getName(),
            DesignToolbar.View.CONTEXT);
      } else if (selectedFileEditor instanceof YaBlocksEditor) {
        YaBlocksEditor blocksEditor = (YaBlocksEditor) selectedFileEditor;
        designToolbar.switchToContext(projectId, blocksEditor.getContext().getName(),
            DesignToolbar.View.BLOCKS);
      } else {
        // shouldn't happen!
        OdeLog.elog("YaProjectEditor got onShow when selectedFileEditor" 
            + " is not a form editor or a blocks editor!");
        ErrorReporter.reportError("Internal error: can't switch file editors.");
      }
    }
  }

  @Override
  protected void onHide() {
    OdeLog.log("YaProjectEditor: got onHide");
    AssetListBox.getAssetListBox().getAssetList().refreshAssetList(0);

    FileEditor selectedFileEditor = getSelectedFileEditor();
    if (selectedFileEditor != null) {
      selectedFileEditor.onHide();
    }
  }
  
  @Override
  protected void onUnload() {
    OdeLog.log("YaProjectEditor: got onUnload");
    super.onUnload();
    for (EditorSet editors : editorMap.values()) {
      editors.blocksEditor.prepareForUnload();
    }
  }

  // ProjectChangeListener methods

  @Override
  public void onProjectLoaded(Project project) {
  }

  @Override
  public void onProjectNodeAdded(Project project, ProjectNode node) {
    String contextName = null;
    boolean isTask = false;
    if (node instanceof YoungAndroidFormNode) {
      if (getFileEditor(node.getFileId()) == null) {
        addFormEditor((YoungAndroidFormNode) node);
        contextName = ((YoungAndroidFormNode) node).getContextName();
      }
    } else if (node instanceof YoungAndroidTaskNode) {
      if (getFileEditor(node.getFileId()) == null) {
        addTaskEditor((YoungAndroidTaskNode) node);
        contextName = ((YoungAndroidTaskNode) node).getContextName();
        isTask = true;
      }

    } else if (node instanceof YoungAndroidBlocksNode) {
      if (getFileEditor(node.getFileId()) == null) {
        addBlocksEditor((YoungAndroidBlocksNode) node);
        contextName = ((YoungAndroidBlocksNode) node).getContextName();
      }
    }
    if (contextName != null) {
      // see if we have both editors yet
      EditorSet editors = editorMap.get(contextName);
      if (editors.contextEditor != null && editors.blocksEditor != null) {
        if (isTask) {
          Ode.getInstance().getDesignToolbar().addTask(node.getProjectId(), contextName,
              editors.contextEditor, editors.blocksEditor);
        }
        else {
          Ode.getInstance().getDesignToolbar().addScreen(node.getProjectId(), contextName,
              editors.contextEditor, editors.blocksEditor);
        }
      }
    }
  }


  @Override
  public void onProjectNodeRemoved(Project project, ProjectNode node) {
    // remove blocks and/or form editor if applicable. Remove screen from 
    // DesignToolbar. If the partner node to this one (blocks or form) was already 
    // removed, calling DesignToolbar.removeScreen a second time will be a no-op.
    OdeLog.log("YaProjectEditor: got onProjectNodeRemoved for project "
            + project.getProjectId() + ", node " + node.getFileId());
    String contextName = null;
    if (node instanceof YoungAndroidFormNode) {
      contextName = ((YoungAndroidFormNode) node).getContextName();
      removeFormEditor(contextName);
    } else if (node instanceof YoungAndroidTaskNode) {
      contextName = ((YoungAndroidTaskNode) node).getContextName();
      removeTaskEditor(contextName);
    } else if (node instanceof YoungAndroidBlocksNode) {
      contextName = ((YoungAndroidBlocksNode) node).getContextName();
      removeBlocksEditor(contextName);
    }
  }
  
  /*
   * Returns the YaBlocksEditor for the given form name in this project
   */
  public YaBlocksEditor getBlocksFileEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      return editorMap.get(formName).blocksEditor;
    } else {
      return null;
    }
  }

  /* 
   * Returns the YaContextEditor for the given context name in this project
   */
  public YaContextEditor getContextFileEditor(String contextName) {
    if (editorMap.containsKey(contextName)) {
      return editorMap.get(contextName).contextEditor;
    } else {
      return null;
    }
  }

  public ArrayList<YaBlocksEditor> getAllTaskBlocksEditors() {
    ArrayList<YaBlocksEditor> taskBlocksEditors = new ArrayList<YaBlocksEditor>();
    for (EditorSet set : editorMap.values()) {
      if (set.blocksEditor.isTaskBlocksEditor()) {
        taskBlocksEditors.add(set.blocksEditor);
      }
    }
    return taskBlocksEditors;
  }

  /**
   * @return a list of component instance names
   */
  public List<String> getComponentInstances(String formName) {
    List<String> components = new ArrayList<String>();
    EditorSet editorSet = editorMap.get(formName);
    if (editorSet == null) {
      return components;
    }
    components.addAll(editorSet.contextEditor.getComponents().keySet());
    return  components;
  }

  public List<String> getComponentInstances() {
    List<String> components = new ArrayList<String>();
    for (String formName : editorMap.keySet()) {
      components.addAll(getComponentInstances(formName));
    }
    return components;
  }

  // Private methods

  private static Comparator<String> getFileIdComparator() {
    // File editors (YaContextEditors and YaBlocksEditors) are sorted so that Screen1 always comes
    // first and others are in alphabetical order. Within each pair, the YaContextEditor is
    // immediately before the YaBlocksEditor.
    return new Comparator<String>() {
      @Override
      public int compare(String fileId1, String fileId2) {
        boolean isContext1 = fileId1.endsWith(YoungAndroidSourceAnalyzer.FORM_PROPERTIES_EXTENSION) ||
                          fileId1.endsWith(YoungAndroidSourceAnalyzer.TASK_PROPERTIES_EXTENSION);
        boolean isContext2 = fileId2.endsWith(YoungAndroidSourceAnalyzer.FORM_PROPERTIES_EXTENSION) ||
                          fileId1.endsWith(YoungAndroidSourceAnalyzer.TASK_PROPERTIES_EXTENSION);

        // Give priority to screen1.
        if (YoungAndroidSourceNode.isScreen1(fileId1)) {
          if (YoungAndroidSourceNode.isScreen1(fileId2)) {
            // They are both named screen1. The form editor should come before the blocks editor.
            if (isContext1) {
              return isContext2 ? 0 : -1;
            } else {
              return isContext2 ? 1 : 0;
            }
          } else {
            // Only fileId1 is named screen1.
            return -1;
          }
        } else if (YoungAndroidSourceNode.isScreen1(fileId2)) {
          // Only fileId2 is named screen1.
          return 1;
        }

        String fileId1WithoutExtension = StorageUtil.trimOffExtension(fileId1);
        String fileId2WithoutExtension = StorageUtil.trimOffExtension(fileId2);
        int compare = fileId1WithoutExtension.compareTo(fileId2WithoutExtension);
        if (compare != 0) {
          return compare;
        }
        // They are both the same name without extension. The form editor should come before the
        // blocks editor.
        if (isContext1) {
          return isContext2 ? 0 : -1;
        } else {
          return isContext2 ? 1 : 0;
        }
      }
    };
  }
  
  private void addFormEditor(YoungAndroidFormNode formNode) {
    final YaFormEditor newFormEditor = new YaFormEditor(this, formNode);
    final String formName = formNode.getContextName();
    OdeLog.log("Adding form editor for " + formName);
    if (editorMap.containsKey(formName)) {
      // This happens if the blocks editor was already added.
      editorMap.get(formName).contextEditor = newFormEditor;
    } else {
      EditorSet editors = new EditorSet();
      editors.contextEditor = newFormEditor;
      editorMap.put(formName, editors);
    }
    newFormEditor.loadFile(new Command() {
      @Override
      public void execute() {
        int pos = Collections.binarySearch(fileIds, newFormEditor.getFileId(),
            getFileIdComparator());
        if (pos < 0) {
          pos = -pos - 1;
        }
        insertFileEditor(newFormEditor, pos);
        if (isScreen1(formName)) {
          screen1FormLoaded = true;
          if (readyToShowScreen1()) {
            OdeLog.log("YaProjectEditor.addFormEditor.loadFile.execute: switching to screen " 
                + formName + " for project " + newFormEditor.getProjectId());
            Ode.getInstance().getDesignToolbar().switchToContext(newFormEditor.getProjectId(),
                formName, DesignToolbar.View.CONTEXT);
          }
        }
        loadBlocksEditor(formName);
      }
    });
  }

  private void addTaskEditor(YoungAndroidTaskNode taskNode) {
    final YaTaskEditor newTaskEditor = new YaTaskEditor(this, taskNode);
    final String taskName = taskNode.getContextName();
    OdeLog.log("Adding task editor for " + taskName + " " + editorMap.keySet() + "!!!");
    if (editorMap.containsKey(taskName)) {
      // This happens if the blocks editor was already added.
      editorMap.get(taskName).contextEditor = newTaskEditor;
    } else {
      EditorSet editors = new EditorSet();
      editors.contextEditor = newTaskEditor;
      editorMap.put(taskName, editors);
    }
    newTaskEditor.loadFile(new Command() {
      @Override
      public void execute() {
        int pos = Collections.binarySearch(fileIds, newTaskEditor.getFileId(),
            getFileIdComparator());
        OdeLog.wlog("Adding task editor " + newTaskEditor.getFileId());
        if (pos < 0) {
          pos = -pos - 1;
        }
        insertFileEditor(newTaskEditor, pos);
        loadBlocksEditor(taskName);
      }
    });
  }

  private boolean readyToShowScreen1() {
    return screen1FormLoaded && screen1BlocksLoaded && screen1Added;
  }

  private boolean readyToLoadProject() {
    return externalComponentsLoaded;
  }

  private void addBlocksEditor(YoungAndroidBlocksNode blocksNode) {
    final YaBlocksEditor newBlocksEditor = new YaBlocksEditor(this, blocksNode);
    final String formName = blocksNode.getContextName();
    OdeLog.log("Adding blocks editor for " + formName);
    if (editorMap.containsKey(formName)) {
      // This happens if the form editor was already added.
      editorMap.get(formName).blocksEditor = newBlocksEditor;
    } else {
      EditorSet editors = new EditorSet();
      editors.blocksEditor = newBlocksEditor;
      editorMap.put(formName, editors);
    }
  }
  
  private void removeFormEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      EditorSet editors = editorMap.get(formName);
      if (editors.blocksEditor == null) {
        editorMap.remove(formName);
      } else {
        editors.contextEditor = null;
      }
    }
  }

  private void removeTaskEditor(String taskName) {
    if (editorMap.containsKey(taskName)) {
      EditorSet editors = editorMap.get(taskName);
      if (editors.blocksEditor == null) {
        editorMap.remove(taskName);
      } else {
        editors.contextEditor = null;
      }
    }
  }

  private void removeBlocksEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      EditorSet editors = editorMap.get(formName);
      if (editors.contextEditor == null) {
        editorMap.remove(formName);
      } else {
        editors.blocksEditor = null;
      }
    }    
  }
  
  public void addComponent(final ProjectNode node, final Command afterComponentAdded) {
    final ProjectNode compNode = node;
    final String fileId = compNode.getFileId();
    AsyncCallback<ChecksumedLoadFile> callback = new OdeAsyncCallback<ChecksumedLoadFile>(MESSAGES.loadError()) {
      @Override
      public void onSuccess(ChecksumedLoadFile result) {
        String jsonFileContent;
        try {
          jsonFileContent = result.getContent();
        } catch (ChecksumedFileException e) {
          this.onFailure(e);
          return;
        }
        JSONValue value = new ClientJsonParser().parse(jsonFileContent);
        COMPONENT_DATABASE.addComponentDatabaseListener(YaProjectEditor.this);
        if (value instanceof JSONArray) {
          JSONArray componentList = value.asArray();
          COMPONENT_DATABASE.addComponents(componentList);
          for (JSONValue component : componentList.getElements()) {
            String name = component.asObject().get("type").asString().getString();
            // group new extensions by package name
            String packageName = name.substring(0, name.lastIndexOf('.'));
            if (!externalCollections.containsKey(packageName)) {
              externalCollections.put(packageName, new HashSet<String>());
            }
            externalCollections.get(packageName).add(name);
            name = packageName;
            if (!externalComponents.contains(name)) {
              externalComponents.add(name);
            }
          }
        } else {
          JSONObject componentJSONObject = value.asObject();
          COMPONENT_DATABASE.addComponent(componentJSONObject);
          // In case of upgrade, we do not need to add entry
          if (!externalComponents.contains(componentJSONObject.get("type").toString())) {
            externalComponents.add(componentJSONObject.get("type").toString());
          }
        }
        numExternalComponentsLoaded++;
        if (afterComponentAdded != null) {
          afterComponentAdded.execute();
        }
      }
      @Override
      public void onFailure(Throwable caught) {
        if (caught instanceof ChecksumedFileException) {
          Ode.getInstance().recordCorruptProject(projectId, fileId, caught.getMessage());
        }
        super.onFailure(caught);
      }
    };
    Ode.getInstance().getProjectService().load2(projectId, fileId, callback);
  }

  /**
   * To remove Component Files from the Project!
   * @param componentTypes
   */
  public  void removeComponent(Map<String, String> componentTypes) {
    final Ode ode = Ode.getInstance();
    final YoungAndroidComponentsFolder componentsFolder = ((YoungAndroidProjectNode) project.getRootNode()).getComponentsFolder();
    Set<String> externalCompFolders = new HashSet<String>();
    // Old projects with old extensions will use FQCN for the directory name rather than the package
    // Prefer deleting com.foo.Bar over com.foo if com.foo.Bar exists.
    for (ProjectNode child : componentsFolder.getChildren()) {
      String[] parts = child.getFileId().split("/");
      if (parts.length >= 3) {
        externalCompFolders.add(parts[2]);
      }
    }
    Set<String> removedPackages = new HashSet<String>();
    for (String componentType : componentTypes.keySet()) {
      String typeName = componentTypes.get(componentType);
      if (!externalCompFolders.contains(typeName) && !externalComponents.contains(typeName)) {
        typeName = typeName.substring(0, typeName.lastIndexOf('.'));
        if (removedPackages.contains(typeName)) {
          continue;
        }
        removedPackages.add(typeName);
      }
      final String directory = componentsFolder.getFileId() + "/" + typeName + "/";
      ode.getProjectService().deleteFolder(ode.getSessionId(), this.projectId, directory,
          new AsyncCallback<Long>() {
            @Override
            public void onFailure(Throwable throwable) {

            }
            @Override
            public void onSuccess(Long date) {
              Iterable<ProjectNode> nodes = componentsFolder.getChildren();
              for (ProjectNode node : nodes) {
                if (node.getFileId().startsWith(directory)) {
                  ode.getProjectManager().getProject(node).deleteNode(node);
                  ode.updateModificationDate(node.getProjectId(), date);
                }
              }
              // Change in extensions requires companion refresh
              YaBlocksEditor.resendExtensionsList();
            }
          });
    }
  }

  private void callLoadProject() {
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        if (!readyToLoadProject()) { // wait till project is processed
          Scheduler.get().scheduleDeferred(this);
        } else {
          loadProject();
        }
      }
    });
  }

  private void loadExternalComponents() {
    //Get the list of all ComponentNodes to be Added
    List<ProjectNode> componentNodes = new ArrayList<ProjectNode>();
    YoungAndroidComponentsFolder componentsFolder = ((YoungAndroidProjectNode) project.getRootNode()).getComponentsFolder();
    if (componentsFolder != null) {
      for (ProjectNode node : componentsFolder.getChildren()) {
        // Find all components that are json files.
        final String nodeName = node.getName();
        if (nodeName.endsWith(".json") && StringUtils.countMatches(node.getFileId(), "/") == 3 ) {
          componentNodes.add(node);
        }
      }
    }
    final int componentCount = componentNodes.size();
    for (ProjectNode componentNode : componentNodes) {
      addComponent(componentNode, new Command() {
        @Override
        public void execute() {
          if (componentCount == numExternalComponentsLoaded) { // true for the last component added
            externalComponentsLoaded = true;
          }
        }
      });
    }
    if (componentCount == 0) {
      externalComponentsLoaded = true; // to hint that we are ready to load
    }
  }

  private void resetExternalComponents() {
    COMPONENT_DATABASE.addComponentDatabaseListener(this);
    COMPONENT_DATABASE.resetDatabase();
    externalComponents.clear();
    numExternalComponentsLoaded = 0;
  }

  private static boolean isScreen1(String formName) {
    return formName.equals(YoungAndroidSourceNode.SCREEN1_FORM_NAME);
  }

  public void addComponentDatbaseListener(ComponentDatabaseChangeListener cdbChangeListener) {
    componentDatabaseChangeListeners.add(cdbChangeListener);
  }

  public void removeComponentDatbaseListener(ComponentDatabaseChangeListener cdbChangeListener) {
    componentDatabaseChangeListeners.remove(cdbChangeListener);
  }

  public void clearComponentDatabaseListeners() {
    componentDatabaseChangeListeners.clear();
  }

  @Override
  public void onComponentTypeAdded(List<String> componentTypes) {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onComponentTypeAdded(componentTypes);
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      editors.contextEditor.onComponentTypeAdded(componentTypes);
      editors.blocksEditor.onComponentTypeAdded(componentTypes);
    }
    // Change of extensions...
    YaBlocksEditor.resendAssetsAndExtensions();
  }

  @Override
  public boolean beforeComponentTypeRemoved(List<String> componentTypes) {
    boolean result = true;
    Set<String> removedTypes = new HashSet<>(componentTypes);
    // aggregate types in the same package
    for (String type : removedTypes) {
      String packageName = COMPONENT_DATABASE.getComponentType(type);
      packageName = packageName.substring(0, packageName.lastIndexOf('.'));
      if (externalCollections.containsKey(packageName)) {
        for (String siblingType : externalCollections.get(packageName)) {
          String siblingName = siblingType.substring(siblingType.lastIndexOf('.') + 1);
          if (!removedTypes.contains(siblingName)) {
            componentTypes.add(siblingName);
          }
        }
      }
    }
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      result = result & cdbChangeListener.beforeComponentTypeRemoved(componentTypes);
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      result = result & editors.contextEditor.beforeComponentTypeRemoved(componentTypes);
      result = result & editors.blocksEditor.beforeComponentTypeRemoved(componentTypes);
    }
    return result;
  }

  @Override
  public void onComponentTypeRemoved(Map<String, String> componentTypes) {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onComponentTypeRemoved(componentTypes);
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      editors.contextEditor.onComponentTypeRemoved(componentTypes);
      editors.blocksEditor.onComponentTypeRemoved(componentTypes);
    }
    removeComponent(componentTypes);
  }

  @Override
  public void onResetDatabase() {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onResetDatabase();
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      editors.contextEditor.onResetDatabase();
      editors.blocksEditor.onResetDatabase();
    }
  }
}

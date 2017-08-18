// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.youngandroid;

import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.boxes.AssetListBox;
import com.google.appinventor.client.boxes.PaletteBox;
import com.google.appinventor.client.boxes.PropertiesBox;
import com.google.appinventor.client.boxes.SourceStructureBox;
import com.google.appinventor.client.editor.ProjectEditor;
import com.google.appinventor.client.editor.simple.SimpleNonVisibleComponentsPanel;
import com.google.appinventor.client.editor.simple.SimpleVisibleComponentsPanel;
import com.google.appinventor.client.editor.simple.components.*;
import com.google.appinventor.client.editor.simple.palette.DropTargetProvider;
import com.google.appinventor.client.editor.simple.palette.SimpleComponentDescriptor;
import com.google.appinventor.client.editor.simple.palette.SimplePalettePanel;
import com.google.appinventor.client.editor.youngandroid.palette.YoungAndroidPalettePanel;
import com.google.appinventor.client.explorer.SourceStructureExplorer;
import com.google.appinventor.client.explorer.project.ComponentDatabaseChangeListener;
import com.google.appinventor.client.output.OdeLog;
import com.google.appinventor.client.widgets.dnd.DropTarget;
import com.google.appinventor.client.widgets.properties.EditableProperties;
import com.google.appinventor.client.widgets.properties.PropertiesPanel;
import com.google.appinventor.client.youngandroid.YoungAndroidFormUpgrader;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.shared.properties.json.JSONObject;
import com.google.appinventor.shared.properties.json.JSONValue;
import com.google.appinventor.shared.rpc.project.ChecksumedFileException;
import com.google.appinventor.shared.rpc.project.ChecksumedLoadFile;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidTaskNode;
import com.google.appinventor.shared.youngandroid.YoungAndroidSourceAnalyzer;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.DockPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.appinventor.client.Ode.MESSAGES;

/**
 * Editor for Young Android Task (.tsk) files.
 *
 * <p>This editor shows a designer that provides support for visual design of
 * tasks.</p>
 */
public final class YaTaskEditor extends YaContextEditor implements ContextChangeListener, ComponentDatabaseChangeListener {

  private final YoungAndroidTaskNode taskNode;

  // Flag to indicate when loading the file is completed. This is needed because building the mock
  // task from the file properties fires events that need to be ignored, otherwise the file will be
  // marked as being modified.
  private boolean loadComplete;

  // References to other panels that we need to control.
  private final SourceStructureExplorer sourceStructureExplorer;

  // Panels that are used as the content of the palette and properties boxes.
  private final YoungAndroidPalettePanel palettePanel;
  private final PropertiesPanel designProperties;

  // UI elements
  private final SimpleNonVisibleComponentsPanel nonVisibleComponentsPanel;

  private MockTask task;  // initialized lazily after the file is loaded from the ODE server

  private String preUpgradeJsonString;

  private final List<ComponentDatabaseChangeListener> componentDatabaseChangeListeners = new ArrayList<ComponentDatabaseChangeListener>();


  /**
   * Creates a new YaTaskEditor.
   *
   * @param projectEditor  the project editor that contains this file editor
   * @param taskNode the YoungAndroidTaskNode associated with this YaTaskEditor
   */
  YaTaskEditor(ProjectEditor projectEditor, YoungAndroidTaskNode taskNode) {
    super(projectEditor, taskNode);
    this.taskNode = taskNode;

    // Get reference to the source structure explorer
    sourceStructureExplorer =
        SourceStructureBox.getSourceStructureBox().getSourceStructureExplorer();

    // Create UI elements for the designer panels.
    nonVisibleComponentsPanel = new SimpleNonVisibleComponentsPanel();
    nonVisibleComponentsPanel.setShowAlways(true);
    DockPanel componentsPanel = new DockPanel();
    componentsPanel.setHorizontalAlignment(DockPanel.ALIGN_CENTER);
    componentsPanel.add(nonVisibleComponentsPanel, DockPanel.NORTH);
    componentsPanel.setSize("100%", "100%");

    // Create palettePanel, which will be used as the content of the PaletteBox.
    palettePanel = new YoungAndroidPalettePanel(this);
    palettePanel.loadComponents(new DropTargetProvider() {
      @Override
      public DropTarget[] getDropTargets() {
        // TODO(markf): Figure out a good way to memorize the targets or refactor things so that
        // getDropTargets() doesn't get called for each component.
        // NOTE: These targets must be specified in depth-first order.
        List<DropTarget> dropTargets = task.getDropTargetsWithin();
        dropTargets.add(nonVisibleComponentsPanel);
        return dropTargets.toArray(new DropTarget[dropTargets.size()]);
      }
    });
    palettePanel.setSize("100%", "100%");

    // Create designProperties, which will be used as the content of the PropertiesBox.
    designProperties = new PropertiesPanel();
    designProperties.setSize("100%", "100%");

    initWidget(componentsPanel);
    setSize("100%", "100%");
  }

  // FileEditor methods

  @Override
  public void loadFile(final Command afterFileLoaded) {
    final long projectId = getProjectId();
    final String fileId = getFileId();
    OdeAsyncCallback<ChecksumedLoadFile> callback = new OdeAsyncCallback<ChecksumedLoadFile>(MESSAGES.loadError()) {
      @Override
      public void onSuccess(ChecksumedLoadFile result) {
        String contents;
        try {
          contents = result.getContent();
        } catch (ChecksumedFileException e) {
          this.onFailure(e);
          return;
        }
        final FileContentHolder fileContentHolder = new FileContentHolder(contents);
        upgradeFile(fileContentHolder, new Command() {
          @Override
          public void execute() {
            onFileLoaded(fileContentHolder.getFileContent());
            if (afterFileLoaded != null) {
              afterFileLoaded.execute();
            }
          }
        });
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

  @Override
  public String getTabText() {
    return taskNode.getContextName();
  }

  @Override
  public void onShow() {
    OdeLog.log("YaTaskEditor: got onShow() for " + getFileId());
    super.onShow();
    loadDesigner();
  }

  @Override
  public void onHide() {
    OdeLog.log("YaTaskEditor: got onHide() for " + getFileId());
    // When an editor is detached, if we are the "current" editor,
    // set the current editor to null and clean up the UI.
    // Note: I'm not sure it is possible that we would not be the "current"
    // editor when this is called, but we check just to be safe.
    if (Ode.getInstance().getCurrentFileEditor() == this) {
      super.onHide();
      unloadDesigner();
    } else {
      OdeLog.wlog("YaTaskEditor.onHide: Not doing anything since we're not the "
          + "current file editor!");
    }
  }

  @Override
  public void onClose() {
    task.removeContextChangeListener(this);
    // Note: our partner YaBlocksEditor will remove itself as a FormChangeListener, even
    // though we added it.
  }

  @Override
  public String getRawFileContent() {
    String encodedProperties = encodeContextAsJsonString(false);
    JSONObject propertiesObject = JSON_PARSER.parse(encodedProperties).asObject();
    return YoungAndroidSourceAnalyzer.generateSourceFile(propertiesObject);
  }

  @Override
  public void onSave() {
  }

  // SimpleEditor methods

  @Override
  public boolean isLoadComplete() {
    return loadComplete;
  }

  @Override
  public Map<String, MockComponent> getComponents() {
    Map<String, MockComponent> map = Maps.newHashMap();
    if (loadComplete) {
      populateComponentsMap(task, map);
    }
    return map;
  }

  @Override
  public List<String> getComponentNames() {
    return new ArrayList<String>(getComponents().keySet());
  }

  @Override
  public SimplePalettePanel getComponentPalettePanel() {
    return palettePanel;
  }

  @Override
  public SimpleNonVisibleComponentsPanel getNonVisibleComponentsPanel() {
    return nonVisibleComponentsPanel;
  }

  @Override
  public SimpleVisibleComponentsPanel getVisibleComponentsPanel() {
    return null;
  }

  @Override
  public boolean isScreen1() {
    return taskNode.isScreen1();
  }

  // FormChangeListener implementation

  @Override
  public void onComponentPropertyChanged(MockComponent component,
                                         String propertyName, String propertyValue) {
    if (loadComplete) {
      // If the property isn't actually persisted to the .scm file, we don't need to do anything.
      if (component.isPropertyPersisted(propertyName)) {
        Ode.getInstance().getEditorManager().scheduleAutoSave(this);
        updatePhone();          // Push changes to the phone if it is connected
      }
    } else {
      OdeLog.elog("onComponentPropertyChanged called when loadComplete is false");
    }
  }

  @Override
  public void onComponentRemoved(MockComponent component, boolean permanentlyDeleted) {
    if (loadComplete) {
      if (permanentlyDeleted) {
        onFormStructureChange();
      }
    } else {
      OdeLog.elog("onComponentRemoved called when loadComplete is false");
    }
  }

  @Override
  public void onComponentAdded(MockComponent component) {
    if (loadComplete) {
      onFormStructureChange();
    } else {
      OdeLog.elog("onComponentAdded called when loadComplete is false");
    }
  }

  @Override
  public void onComponentRenamed(MockComponent component, String oldName) {
    if (loadComplete) {
      onFormStructureChange();
    } else {
      OdeLog.elog("onComponentRenamed called when loadComplete is false");
    }
  }

  @Override
  public void onComponentSelectionChange(MockComponent component, boolean selected) {
    if (loadComplete) {
      if (selected) {
        // Select the item in the source structure explorer.
        sourceStructureExplorer.selectItem(component.getSourceStructureExplorerItem());

        // Show the component properties in the properties panel.
        updatePropertiesPanel(component);
      } else {
        // Unselect the item in the source structure explorer.
        sourceStructureExplorer.unselectItem(component.getSourceStructureExplorerItem());
      }
    } else {
      OdeLog.elog("onComponentSelectionChange called when loadComplete is false");
    }
  }

  // other public methods

  /**
   * Returns the context associated with this YaTaskEditor.
   *
   * @return a MockContext
   */
  @Override
  public MockContext getContext() {
    return task;
  }

  /**
   * Returns the task associated with this YaTaskEditor.
   *
   * @return a MockForm
   */
  public MockTask getTask() {
    return task;
  }

  public String getComponentInstanceTypeName(String instanceName) {
    return getComponents().get(instanceName).getType();
  }

  // private methods

  /*
   * Upgrades the given file content, saves the upgraded content back to the
   * ODE server, and calls the afterUpgradeComplete command after the save
   * operation succeeds.
   *
   * If no upgrade is necessary, the afterSavingFiles command is called
   * immediately.
   *
   * @param fileContentHolder  holds the file content
   * @param afterUpgradeComplete  optional command to be executed after the
   *                              file has upgraded and saved back to the ODE
   *                              server
   */
  private void upgradeFile(FileContentHolder fileContentHolder,
                           final Command afterUpgradeComplete) {
    JSONObject propertiesObject = YoungAndroidSourceAnalyzer.parseSourceFile(
        fileContentHolder.getFileContent(), JSON_PARSER);
    preUpgradeJsonString =  propertiesObject.toJson();
    if (YoungAndroidFormUpgrader.upgradeSourceProperties(propertiesObject.getProperties())) {
      String upgradedContent = YoungAndroidSourceAnalyzer.generateSourceFile(propertiesObject);
      fileContentHolder.setFileContent(upgradedContent);

      Ode.getInstance().getProjectService().save(Ode.getInstance().getSessionId(),
          getProjectId(), getFileId(), upgradedContent,
          new OdeAsyncCallback<Long>(MESSAGES.saveError()) {
            @Override
            public void onSuccess(Long result) {
              // Execute the afterUpgradeComplete command if one was given.
              if (afterUpgradeComplete != null) {
                afterUpgradeComplete.execute();
              }
            }
          });
    } else {
      // No upgrade was necessary.
      // Execute the afterUpgradeComplete command if one was given.
      if (afterUpgradeComplete != null) {
        afterUpgradeComplete.execute();
      }
    }
  }

  private void onFileLoaded(String content) {
    JSONObject propertiesObject = YoungAndroidSourceAnalyzer.parseSourceFile(
        content, JSON_PARSER);
    task = createMockTask(propertiesObject.getProperties().get("Properties").asObject());

    // Initialize the nonVisibleComponentsPanel and visibleComponentsPanel.
    nonVisibleComponentsPanel.setContext(task);
    task.select();

    // Set loadCompleted to true.
    // From now on, all change events will be taken seriously.
    loadComplete = true;
  }

  /*
   * Parses the JSON properties and creates the task and its component structure.
   */
  private MockTask createMockTask(JSONObject propertiesObject) {
    return (MockTask) createMockComponent(propertiesObject, null);
  }

  /*
   * Parses the JSON properties and creates the component structure. This method is called
   * recursively for nested components. For the initial invocation parent shall be null.
   */
  private MockComponent createMockComponent(JSONObject propertiesObject, MockContainer parent) {
    Map<String, JSONValue> properties = propertiesObject.getProperties();

    // Component name and type
    String componentType = properties.get("$Type").asString().getString();

    // Instantiate a mock component for the visual designer
    MockComponent mockComponent;
    if (componentType.equals(MockTask.TYPE)) {
      Preconditions.checkArgument(parent == null);

      // Instantiate new root component
      mockComponent = new MockTask(this);
    } else {
      mockComponent = SimpleComponentDescriptor.createMockComponent(componentType,
              COMPONENT_DATABASE.getComponentType(componentType), this);

      // Add the component to its parent component (and if it is non-visible, add it to the
      // nonVisibleComponent panel).
      parent.addComponent(mockComponent);
      if (!mockComponent.isVisibleComponent()) {
        nonVisibleComponentsPanel.addComponent(mockComponent);
      }
    }
    // Set the name of the component (on instantiation components are assigned a generated name)
    String componentName = properties.get("$Name").asString().getString();
    mockComponent.changeProperty("Name", componentName);

    // Set component properties
    for (String name : properties.keySet()) {
      if (name.charAt(0) != '$') { // Ignore special properties (name, type and nested components)
        mockComponent.changeProperty(name, properties.get(name).asString().getString());
      }
    }

    // Add component type to the blocks editorA
    YaProjectEditor yaProjectEditor = (YaProjectEditor) projectEditor;
    YaBlocksEditor blockEditor = yaProjectEditor.getBlocksFileEditor(taskNode.getContextName());
    blockEditor.addComponent(mockComponent.getType(), mockComponent.getName(),
        mockComponent.getUuid());

    // Add nested components
    if (properties.containsKey("$Components")) {
      for (JSONValue nestedComponent : properties.get("$Components").asArray().getElements()) {
        createMockComponent(nestedComponent.asObject(), (MockContainer) mockComponent);
      }
    }
    return mockComponent;
  }

  /*
   * Updates the the whole designer: task, palette, source structure explorer,
   * assets list, and properties panel.
   */
  private void loadDesigner() {
    task.refresh();
    MockComponent selectedComponent = task.getSelectedComponent();

    // Set the palette box's content.
    PaletteBox paletteBox = PaletteBox.getPaletteBox();
    paletteBox.setContent(palettePanel);

    // Update the source structure explorer with the tree of this tasks's components.
    sourceStructureExplorer.updateTree(task.buildComponentsTree(),
        selectedComponent.getSourceStructureExplorerItem());
    SourceStructureBox.getSourceStructureBox().setVisible(true);

    // Show the assets box.
    AssetListBox assetListBox = AssetListBox.getAssetListBox();
    assetListBox.setVisible(true);

    // Set the properties box's content.
    PropertiesBox propertiesBox = PropertiesBox.getPropertiesBox();
    propertiesBox.setContent(designProperties);
    updatePropertiesPanel(selectedComponent);
    propertiesBox.setVisible(true);

    // Listen to changes on the task.
    task.addContextChangeListener(this);
    // Also have the blocks editor listen to changes. Do this here instead
    // of in the blocks editor so that we don't risk it missing any updates.
    task.addContextChangeListener(((YaProjectEditor) projectEditor)
        .getBlocksFileEditor(task.getName()));
  }

  /*
   * Show the given component's properties in the properties panel.
   */
  private void updatePropertiesPanel(MockComponent component) {
    designProperties.setProperties(component.getProperties());
    // need to update the caption after the setProperties call, since
    // setProperties clears the caption!
    designProperties.setPropertiesCaption(component.getName());
  }

  private void onFormStructureChange() {
    Ode.getInstance().getEditorManager().scheduleAutoSave(this);

    // Update source structure panel
    sourceStructureExplorer.updateTree(task.buildComponentsTree(),
        task.getSelectedComponent().getSourceStructureExplorerItem());
    updatePhone();          // Push changes to the phone if it is connected
  }

  private void populateComponentsMap(MockComponent component, Map<String, MockComponent> map) {
    EditableProperties properties = component.getProperties();
    map.put(properties.getPropertyValue("Name"), component);
    List<MockComponent> children = component.getChildren();
    for (MockComponent child : children) {
      populateComponentsMap(child, map);
    }
  }

  /*
   * Encodes the task's properties as a JSON encoded string. Used by YaBlocksEditor as well,
   * to send the task info to the blockly world during code generation.
   */
  public String encodeContextAsJsonString(boolean forYail) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    // Include authURL in output if it is non-null
    if (authURL != null) {
      sb.append("\"authURL\":").append(authURL.toJson()).append(",");
    }
    sb.append("\"YaVersion\":\"").append(YaVersion.YOUNG_ANDROID_VERSION).append("\",");
    sb.append("\"Source\":\"Task\",");
    sb.append("\"Properties\":");
    encodeComponentProperties(task, sb, forYail);
    sb.append("}");
    return sb.toString();
  }

  // [lyn, 2014/10/13] returns the *pre-upgraded* JSON for this task.
  // needed to allow associated blocks editor to get this info.
  protected String preUpgradeJsonString() {
    return preUpgradeJsonString;
  }

  /*
   * Encodes a component and its properties into a JSON encoded string.
   */
  private void encodeComponentProperties(MockComponent component, StringBuilder sb, boolean forYail) {
    // The component encoding starts with component name and type
    String componentType = component.getType();
    EditableProperties properties = component.getProperties();
    sb.append("{\"$Name\":\"");
    sb.append(properties.getPropertyValue("Name"));
    sb.append("\",\"$Type\":\"");
    sb.append(componentType);
    sb.append("\",\"$Version\":\"");
    sb.append(COMPONENT_DATABASE.getComponentVersion(componentType));
    sb.append('"');

    // Next the actual component properties
    //
    // NOTE: It is important that these be encoded before any children components.
    String propertiesString = properties.encodeAsPairs(forYail);
    if (propertiesString.length() > 0) {
      sb.append(',');
      sb.append(propertiesString);
    }

    // Finally any children of the component
    List<MockComponent> children = component.getChildren();
    if (!children.isEmpty()) {
      sb.append(",\"$Components\":[");
      String separator = "";
      for (MockComponent child : children) {
        sb.append(separator);
        encodeComponentProperties(child, sb, forYail);
        separator = ",";
      }
      sb.append(']');
    }

    sb.append('}');
  }

  /*
   * Clears the palette, source structure explorer, and properties panel.
   */
  private void unloadDesigner() {
    // The task can still potentially change if the blocks editor is displayed
    // so don't remove the taskChangeListener.

    // Clear the palette box.
    PaletteBox paletteBox = PaletteBox.getPaletteBox();
    paletteBox.clear();

    // Clear and hide the source structure explorer.
    sourceStructureExplorer.clearTree();
    SourceStructureBox.getSourceStructureBox().setVisible(false);

    // Hide the assets box.
    AssetListBox assetListBox = AssetListBox.getAssetListBox();
    assetListBox.setVisible(false);

    // Clear and hide the properties box.
    PropertiesBox propertiesBox = PropertiesBox.getPropertiesBox();
    propertiesBox.clear();
    propertiesBox.setVisible(false);
  }

  /**
   * Runs through all the Mock Components and upgrades if its corresponding Component was Upgraded
   * @param componentTypes the Component Types that got upgraded
   */
  private void updateMockComponents(List<String> componentTypes) {
    Map<String, MockComponent> componentMap = getComponents();
    for (MockComponent mockComponent : componentMap.values()) {
      if (componentTypes.contains(mockComponent.getType())) {
        mockComponent.upgrade();
        mockComponent.upgradeComplete();
      }
    }
  }

  /*
   * Push changes to a connected phone (or emulator).
   */
  private void updatePhone() {
    YaProjectEditor yaProjectEditor = (YaProjectEditor) projectEditor;
    YaBlocksEditor blockEditor = yaProjectEditor.getBlocksFileEditor(taskNode.getContextName());
    blockEditor.sendComponentData();
  }

  @Override
  public void onComponentTypeAdded(List<String> componentTypes) {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onComponentTypeAdded(componentTypes);
    }
    //Update Mock Components
    updateMockComponents(componentTypes);
    //Update the Properties Panel
    updatePropertiesPanel(task.getSelectedComponent());
  }

  @Override
  public boolean beforeComponentTypeRemoved(List<String> componentTypes) {
    boolean result = true;
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      result = result & cdbChangeListener.beforeComponentTypeRemoved(componentTypes);
    }
    List<MockComponent> mockComponents = new ArrayList<MockComponent>(getTask().getChildren());
    for (String compType : componentTypes) {
      for (MockComponent mockComp : mockComponents) {
        if (mockComp.getType().equals(compType)) {
          mockComp.delete();
        }
      }
    }
    return result;
  }

  @Override
  public void onComponentTypeRemoved(Map<String, String> componentTypes) {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onComponentTypeRemoved(componentTypes);
    }
  }

  @Override
  public void onResetDatabase() {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onResetDatabase();
    }
  }

}
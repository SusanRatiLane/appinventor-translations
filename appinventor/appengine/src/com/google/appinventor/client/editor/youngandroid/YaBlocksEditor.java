// -*- mode: java; c-basic-offset: 2; -*-
// Copyright © 2009-2011 Google, All Rights reserved
// Copyright © 2011-2016 Massachusetts Institute of Technology, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0
package com.google.appinventor.client.editor.youngandroid;

import com.google.appinventor.client.ErrorReporter;
import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.boxes.AssetListBox;
import com.google.appinventor.client.boxes.BlockSelectorBox;
import com.google.appinventor.client.boxes.PaletteBox;
import com.google.appinventor.client.editor.FileEditor;
import com.google.appinventor.client.editor.simple.SimpleComponentDatabase;
import com.google.appinventor.client.editor.simple.components.ContextChangeListener;
import com.google.appinventor.client.editor.simple.components.MockComponent;
import com.google.appinventor.client.editor.simple.components.MockContext;
import com.google.appinventor.client.editor.simple.palette.DropTargetProvider;
import com.google.appinventor.client.editor.youngandroid.BlocklyPanel.BlocklyWorkspaceChangeListener;
import com.google.appinventor.client.editor.youngandroid.events.EventHelper;
import com.google.appinventor.client.editor.youngandroid.palette.YoungAndroidPalettePanel;
import com.google.appinventor.client.explorer.SourceStructureExplorer;
import com.google.appinventor.client.explorer.SourceStructureExplorerItem;
import com.google.appinventor.client.explorer.project.ComponentDatabaseChangeListener;
import com.google.appinventor.client.output.OdeLog;
import com.google.appinventor.client.widgets.dnd.DropTarget;
import com.google.appinventor.shared.rpc.project.ChecksumedFileException;
import com.google.appinventor.shared.rpc.project.ChecksumedLoadFile;
import com.google.appinventor.shared.rpc.project.FileDescriptorWithContent;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidBlocksNode;
import com.google.appinventor.shared.youngandroid.YoungAndroidSourceAnalyzer;
import com.google.common.collect.Maps;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.TreeItem;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.appinventor.client.Ode.MESSAGES;

/**
 * Editor for Young Android Blocks (.blk) files.
 *
 * @author lizlooney@google.com (Liz Looney)
 * @author sharon@google.com (Sharon Perl) added Blockly functionality
 */
public final class YaBlocksEditor extends FileEditor
    implements ContextChangeListener, BlockDrawerSelectionListener, ComponentDatabaseChangeListener, BlocklyWorkspaceChangeListener {

  // A constant to substract from the total height of the Viewer window, set through
  // the computed height of the user's window (Window.getClientHeight())
  // This is an approximation of the size of the header navigation panel
  private static final int VIEWER_WINDOW_OFFSET = 170;

  // Database of component type descriptions
  private final SimpleComponentDatabase COMPONENT_DATABASE;

  // Keep a map from projectid_contextname -> YaBlocksEditor for handling blocks workspace changed
  // callbacks from the BlocklyPanel objects. This has to be static because it is used by
  // static methods that are called from the Javascript Blockly world.
  private static final Map<String, YaBlocksEditor> contextToBlocksEditor = Maps.newHashMap();

  // projectid_contextName for this blocks editor. Our index into the static contextToBlocksEditor map.
  private String contextName;

  private final YoungAndroidBlocksNode blocksNode;

  // References to other panels that we need to control.
  private final SourceStructureExplorer sourceStructureExplorer;

  // Panel that is used as the content of the palette box
  private final YoungAndroidPalettePanel palettePanel;

  // Blocks area. Note that the blocks area is a part of the "document" in the
  // browser (via the deckPanel in the ProjectEditor). So if the document changes (which happens
  // when we switch projects) we will lose the blocks editor state, even though
  // YaBlocksEditor objects are kept around when switching projects. If we come
  // back to this blocks editor after having switched projects, the blocksArea
  // will get reinitialized.
  private final BlocklyPanel blocksArea;

  // True once we've finished loading the current file.
  private boolean loadComplete = false;

  // if selectedDrawer != null, it is either "component_" + instance name or
  // "builtin_" + drawer name
  private String selectedDrawer = null;

  // Keep a list of components that we know about. Need this to detect when a call to add a
  // component is adding one that we already have (which can happen when a component gets
  // moved from one container to another). In that case we do not want to add it to the
  // blocks area again.
  private Set<String> componentUuids = new HashSet<String>();

  // The context editor associated with this blocks editor
  private YaContextEditor myContextEditor;

  YaBlocksEditor(YaProjectEditor projectEditor, YoungAndroidBlocksNode blocksNode) {
    super(projectEditor, blocksNode);

    this.blocksNode = blocksNode;
    COMPONENT_DATABASE = SimpleComponentDatabase.getInstance(getProjectId());

    contextName = blocksNode.getProjectId() + "_" + blocksNode.getContextName();
    contextToBlocksEditor.put(contextName, this);
    blocksArea = new BlocklyPanel(this, contextName); // [lyn, 2014/10/28] pass in editor so can extract context json from it
    blocksArea.setWidth("100%");
    // This code seems to be using a rather old layout, so we cannot simply pass 100% for height.
    // Instead, it needs to be calculated from the client's window, and a listener added to Window
    // We use VIEWER_WINDOW_OFFSET as an approximation of the size of the top navigation bar
    // New layouts don't need all this messing; see comments on selected answer at:
    // http://stackoverflow.com/questions/86901/creating-a-fluid-panel-in-gwt-to-fill-the-page
    blocksArea.setHeight(Window.getClientHeight() - VIEWER_WINDOW_OFFSET + "px");
    Window.addResizeHandler(new ResizeHandler() {
     public void onResize(ResizeEvent event) {
       int height = event.getHeight();
       blocksArea.setHeight(height - VIEWER_WINDOW_OFFSET + "px");
     }
    });
    initWidget(blocksArea);
    blocksArea.populateComponentTypes(COMPONENT_DATABASE.getComponentsJSONString());

    // Get references to the source structure explorer
    sourceStructureExplorer = BlockSelectorBox.getBlockSelectorBox().getSourceStructureExplorer();

    // Listen for selection events for built-in drawers
    BlockSelectorBox.getBlockSelectorBox().addBlockDrawerSelectionListener(this);

    // Create palettePanel, which will be used as the content of the PaletteBox.
    myContextEditor = projectEditor.getContextFileEditor(blocksNode.getContextName());
    if (myContextEditor != null) {
      palettePanel = new YoungAndroidPalettePanel(myContextEditor);
      palettePanel.loadComponents(new DropTargetProvider() {
        // TODO(sharon): make the tree in the BlockSelectorBox a drop target
        @Override
        public DropTarget[] getDropTargets() {
          return new DropTarget[0];
        }
      });
      palettePanel.setSize("100%", "100%");
    } else {
      palettePanel = null;
      OdeLog.wlog("Can't get context editor for blocks: " + getFileId());
    }
  }

  // FileEditor methods

  @Override
  public void loadFile(final Command afterFileLoaded) {
    final long projectId = getProjectId();
    final String fileId = getFileId();
    OdeAsyncCallback<ChecksumedLoadFile> callback = new OdeAsyncCallback<ChecksumedLoadFile>(MESSAGES.loadError()) {
      @Override
      public void onSuccess(ChecksumedLoadFile result) {
        String blkFileContent;
        try {
          blkFileContent = result.getContent();
        } catch (ChecksumedFileException e) {
          this.onFailure(e);
          return;
        }
        String contextJson = myContextEditor.preUpgradeJsonString(); // [lyn, 2014/10/27] added contextJson for upgrading
        try {
          blocksArea.loadBlocksContent(contextJson, blkFileContent);
          blocksArea.addChangeListener(YaBlocksEditor.this);
        } catch(LoadBlocksException e) {
          setBlocksDamaged(contextName);
          ErrorReporter.reportError(MESSAGES.blocksNotSaved(contextName));
        }
        loadComplete = true;
        selectedDrawer = null;
        if (afterFileLoaded != null) {
          afterFileLoaded.execute();
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

  @Override
  public String getTabText() {
    return MESSAGES.blocksEditorTabName(blocksNode.getContextName());
  }

  @Override
  public void onShow() {
    OdeLog.log("YaBlocksEditor: got onShow() for " + getFileId());
    super.onShow();
    loadBlocksEditor();
    sendComponentData();  // Send Blockly the component information for generating Yail
  }

  public boolean isFormBlocksEditor() {
    return myContextEditor instanceof YaFormEditor;
  }

  public boolean isTaskBlocksEditor() {
    return myContextEditor instanceof YaTaskEditor;
  }



  /*
   * Updates the the whole designer: context, palette, source structure explorer, assets list, and
   * properties panel.
   */
  private void loadBlocksEditor() {

    // Set the palette box's content.
    if (palettePanel != null) {
      PaletteBox paletteBox = PaletteBox.getPaletteBox();
      paletteBox.setContent(palettePanel);
    }
    PaletteBox.getPaletteBox().setVisible(false);

    // Update the source structure explorer with the tree of this context's components.
    MockContext context = getContext();
    if (context != null) {
      // start with no component selected in sourceStructureExplorer. We
      // don't want a component drawer open in the blocks editor when we
      // come back to it.
      updateBlocksTree(context, null);

      Ode.getInstance().getWorkColumns().remove(Ode.getInstance().getStructureAndAssets()
          .getWidget(2));
      Ode.getInstance().getWorkColumns().insert(Ode.getInstance().getStructureAndAssets(), 1);
      Ode.getInstance().getStructureAndAssets().insert(BlockSelectorBox.getBlockSelectorBox(), 0);
      BlockSelectorBox.getBlockSelectorBox().setVisible(true);
      AssetListBox.getAssetListBox().setVisible(true);
      blocksArea.injectWorkspace();
      hideComponentBlocks();
    } else {
      OdeLog.wlog("Can't get context editor for blocks: " + getFileId());
    }
  }

  @Override
  public void onHide() {
    // When an editor is detached, if we are the "current" editor,
    // set the current editor to null and clean up the UI.
    // Note: I'm not sure it is possible that we would not be the "current"
    // editor when this is called, but we check just to be safe.
    OdeLog.log("YaBlocksEditor: got onHide() for " + getFileId());
    if (Ode.getInstance().getCurrentFileEditor() == this) {
      super.onHide();
      unloadBlocksEditor();
    } else {
      OdeLog.wlog("YaBlocksEditor.onHide: Not doing anything since we're not the "
          + "current file editor!");
    }
  }

  @Override
  public void onClose() {
    // our partner YaContextEditor added us as a ContextChangeListener, but we remove ourself.
    getContext().removeContextChangeListener(this);
    BlockSelectorBox.getBlockSelectorBox().removeBlockDrawerSelectionListener(this);
    contextToBlocksEditor.remove(contextName);
  }

  public static void toggleWarning() {
    BlocklyPanel.switchWarningVisibility();
    for(YaBlocksEditor editor : contextToBlocksEditor.values()){
      editor.blocksArea.toggleWarning();
    }
  }

  private void unloadBlocksEditor() {
    // TODO(sharon): do something about context change listener?

    // Clear the palette box.
    PaletteBox paletteBox = PaletteBox.getPaletteBox();
    paletteBox.clear();
    paletteBox.setVisible(true);

    Ode.getInstance().getWorkColumns().remove(Ode.getInstance().getStructureAndAssets().getWidget(0));
    Ode.getInstance().getWorkColumns().insert(Ode.getInstance().getStructureAndAssets(), 3);
    Ode.getInstance().getStructureAndAssets().insert(BlockSelectorBox.getBlockSelectorBox(), 0);
    BlockSelectorBox.getBlockSelectorBox().setVisible(false);
    AssetListBox.getAssetListBox().setVisible(true);

    // Clear and hide the blocks selector tree
    sourceStructureExplorer.clearTree();
    hideComponentBlocks();
    blocksArea.hideChaff();
  }

  @Override
  public void onWorkspaceChange(BlocklyPanel panel, JavaScriptObject event) {
    if (!EventHelper.isTransient(event)) {
      Ode.getInstance().getEditorManager().scheduleAutoSave(this);
    }
    sendComponentData();
  }

  @Override
  public void getBlocksImage(Callback<String, String> callback) {
    blocksArea.getBlocksImage(callback);
  }

  public synchronized void sendComponentData() {
    try {
      blocksArea.sendComponentData(myContextEditor.encodeContextAsJsonString(true),
        packageNameFromPath(getFileId()));
    } catch (YailGenerationException e) {
      e.printStackTrace();
    }
  }

  private void updateBlocksTree(MockContext context, SourceStructureExplorerItem itemToSelect) {
    TreeItem items[] = new TreeItem[3];
    items[0] = BlockSelectorBox.getBlockSelectorBox().getBuiltInBlocksTree();
    items[1] = context.buildComponentsTree();
    items[2] = BlockSelectorBox.getBlockSelectorBox().getGenericComponentsTree(context);
    sourceStructureExplorer.updateTree(items, itemToSelect);
  }

  // Do whatever is needed to save Blockly state when our project is about to be
  // detached from the parent document. Note that this is not for saving the blocks file itself.
  // We use EditorManager.scheduleAutoSave for that.
  public void prepareForUnload() {
    blocksArea.saveComponentsAndBlocks();
//    blocksArea.saveBackpackContents();
  }

  @Override
  public String getRawFileContent() {
    return blocksArea.getBlocksContent();
  }

  public FileDescriptorWithContent getYail() throws YailGenerationException {
    return new FileDescriptorWithContent(getProjectId(), yailFileName(),
        blocksArea.getYail(myContextEditor.encodeContextAsJsonString(true),
            packageNameFromPath(getFileId())));
  }

  /**
   * Converts a source file path (e.g.,
   * src/com/gmail/username/project1/Context.extension) into a package
   * name (e.g., com.gmail.username.project1.Context)
   * @param path the path to convert.
   * @return a dot separated package name.
   */
  private static String packageNameFromPath(String path) {
    path = path.replaceFirst("src/", "");
    int extensionIndex = path.lastIndexOf(".");
    if (extensionIndex != -1) {
      path = path.substring(0, extensionIndex);
    }
    return path.replaceAll("/", ".");
  }

  @Override
  public void onSave() {
    // Nothing to do after blocks are saved.
  }

  public static String getComponentInfo(String typeName) {
    return SimpleComponentDatabase.getInstance().getTypeDescription(typeName);
  }

  public static String getComponentsJSONString(long projectId) {
    return SimpleComponentDatabase.getInstance(projectId).getComponentsJSONString();
  }

  public static String getComponentInstanceTypeName(String contextName, String instanceName) {
      //use context name to get blocks editor
      YaBlocksEditor blocksEditor = contextToBlocksEditor.get(contextName);
      //get type name from context editor
      return blocksEditor.myContextEditor.getComponentInstanceTypeName(instanceName);
  }

  public void addComponent(String typeName, String instanceName, String uuid) {
    if (componentUuids.add(uuid)) {
      blocksArea.addComponent(uuid, instanceName, typeName);
    }
  }

  public void removeComponent(String typeName, String instanceName, String uuid) {
    if (componentUuids.remove(uuid)) {
      blocksArea.removeComponent(uuid);
    }
  }

  public void renameComponent(String oldName, String newName, String uuid) {
    blocksArea.renameComponent(uuid, oldName, newName);
  }

  public void showComponentBlocks(String instanceName) {
    String instanceDrawer = "component_" + instanceName;
    if (selectedDrawer == null || !blocksArea.drawerShowing()
        || !selectedDrawer.equals(instanceDrawer)) {
      blocksArea.showComponentBlocks(instanceName);
      selectedDrawer = instanceDrawer;
    } else {
      blocksArea.hideDrawer();
      selectedDrawer = null;
    }
  }

  public void hideComponentBlocks() {
    blocksArea.hideDrawer();
    selectedDrawer = null;
  }

  public void showBuiltinBlocks(String drawerName) {
    OdeLog.log("Showing built-in drawer " + drawerName);
    String builtinDrawer = "builtin_" + drawerName;
    if (selectedDrawer == null || !blocksArea.drawerShowing()
        || !selectedDrawer.equals(builtinDrawer)) {
      blocksArea.showBuiltinBlocks(drawerName);
      selectedDrawer = builtinDrawer;
    } else {
      blocksArea.hideDrawer();
      selectedDrawer = null;
    }
  }

  public void showGenericBlocks(String drawerName) {
    OdeLog.log("Showing generic drawer " + drawerName);
    String genericDrawer = "generic_" + drawerName;
    if (selectedDrawer == null || !blocksArea.drawerShowing()
        || !selectedDrawer.equals(genericDrawer)) {
      blocksArea.showGenericBlocks(drawerName);
      selectedDrawer = genericDrawer;
    } else {
      blocksArea.hideDrawer();
      selectedDrawer = null;
    }
  }

  public void hideBuiltinBlocks() {
    blocksArea.hideDrawer();
  }

  public MockContext getContext() {
    YaProjectEditor yaProjectEditor = (YaProjectEditor) projectEditor;
    YaContextEditor myContextEditor = yaProjectEditor.getContextFileEditor(blocksNode.getContextName());
    if (myContextEditor != null) {
      return myContextEditor.getContext();
    } else {
      return null;
    }
  }

  private String yailFileName() {
    String fileId = getFileId();
    return fileId.replace(YoungAndroidSourceAnalyzer.BLOCKLY_SOURCE_EXTENSION,
        YoungAndroidSourceAnalyzer.YAIL_FILE_EXTENSION);
  }

  // ContextChangeListener implementation
  // Note: our companion YaContextEditor adds us as a listener on the context

  /*
   * @see com.google.appinventor.client.editor.simple.components.ContextChangeListener#
   * onComponentPropertyChanged
   * (com.google.appinventor.client.editor.simple.components.MockComponent, java.lang.String,
   * java.lang.String)
   */
  @Override
  public void onComponentPropertyChanged(
      MockComponent component, String propertyName, String propertyValue) {
    // nothing to do here
  }

  /*
   * @see
   * com.google.appinventor.client.editor.simple.components.ContextChangeListener#onComponentRemoved
   * (com.google.appinventor.client.editor.simple.components.MockComponent, boolean)
   */
  @Override
  public void onComponentRemoved(MockComponent component, boolean permanentlyDeleted) {
    if (permanentlyDeleted) {
      removeComponent(component.getType(), component.getName(), component.getUuid());
      if (loadComplete) {
        updateSourceStructureExplorer();
      }
    }
  }

  /*
   * @see
   * com.google.appinventor.client.editor.simple.components.ContextChangeListener#onComponentAdded
   * (com.google.appinventor.client.editor.simple.components.MockComponent)
   */
  @Override
  public void onComponentAdded(MockComponent component) {
    addComponent(component.getType(), component.getName(), component.getUuid());
    if (loadComplete) {
      // Update source structure panel
      updateSourceStructureExplorer();
    }
  }

  /*
   * @see
   * com.google.appinventor.client.editor.simple.components.ContextChangeListener#onComponentRenamed
   * (com.google.appinventor.client.editor.simple.components.MockComponent, java.lang.String)
   */
  @Override
  public void onComponentRenamed(MockComponent component, String oldName) {
    renameComponent(oldName, component.getName(), component.getUuid());
    if (loadComplete) {
      updateSourceStructureExplorer();
      // renaming could potentially confuse an open drawer so close just in case
      hideComponentBlocks();
      selectedDrawer = null;
    }
  }

  private void updateSourceStructureExplorer() {
    MockContext context = getContext();
    if (context != null) {
      updateBlocksTree(context, context.getSelectedComponent().getSourceStructureExplorerItem());
    }
  }

  /*
   * @see com.google.appinventor.client.editor.simple.components.ContextChangeListener#
   * onComponentSelectionChange
   * (com.google.appinventor.client.editor.simple.components.MockComponent, boolean)
   */
  @Override
  public void onComponentSelectionChange(MockComponent component, boolean selected) {
    // not relevant for blocks editor - this happens on clicks in the mock context areas
  }

  // BlockDrawerSelectionListener implementation

  /*
   * @see com.google.appinventor.client.editor.youngandroid.BlockDrawerSelectionListener#
   * onBlockDrawerSelected(java.lang.String)
   */
  @Override
  public void onBuiltinDrawerSelected(String drawerName) {
    // Only do something if we are the current file editor
    if (Ode.getInstance().getCurrentFileEditor() == this) {
      showBuiltinBlocks(drawerName);
    }
  }

  /*
   * @see com.google.appinventor.client.editor.youngandroid.BlockDrawerSelectionListener#
   * onBlockDrawerSelected(java.lang.String)
   */
  @Override
  public void onGenericDrawerSelected(String drawerName) {
    // Only do something if we are the current file editor
    if (Ode.getInstance().getCurrentFileEditor() == this) {
      showGenericBlocks(drawerName);
    }
  }

  /*
   * Start up the Repl (call into the Blockly.ReplMgr via the BlocklyPanel.
   */
  @Override
  public void startRepl(boolean alreadyRunning, boolean forEmulator, boolean forUsb) {
    blocksArea.startRepl(alreadyRunning, forEmulator, forUsb);
  }

  /*
   * Perform a Hard Reset of the Emulator
   */
  public void hardReset() {
    blocksArea.hardReset();
  }

  // Static Function. Find the associated editor for contextName and
  // set its "damaged" bit. This will cause the editor manager's scheduleAutoSave
  // method to ignore this blocks file and not save it out.

  public static void setBlocksDamaged(String contextName) {
    YaBlocksEditor editor = contextToBlocksEditor.get(contextName);
    if (editor != null) {
      editor.setDamaged(true);
    }
  }

  public static boolean isFormBlocksEditor(String contextName) {
    YaBlocksEditor editor = contextToBlocksEditor.get(contextName);
    if (editor != null && editor.isFormBlocksEditor()) {
      return true;
    }
    return false;
  }

  public static boolean isTaskBlocksEditor(String contextName) {
    YaBlocksEditor editor = contextToBlocksEditor.get(contextName);
    if (editor != null && editor.isTaskBlocksEditor()) {
      return true;
    }
    return false;
  }

  /*
   * Trigger a Companion Update
   */
  @Override
  public void updateCompanion() {
    blocksArea.updateCompanion();
  }

  /*
   * [lyn, 2014/10/28] Added for accessing current context json from BlocklyPanel
   * Encodes the associated context's properties as a JSON encoded string. Used by YaBlocksEditor as well,
   * to send the context info to the blockly world during code generation.
   */
  protected String encodeContextAsJsonString(boolean forYail) {
    return myContextEditor.encodeContextAsJsonString(forYail);
  }

  @Override
  public void onComponentTypeAdded(List<String> componentTypes) {
    blocksArea.populateComponentTypes(COMPONENT_DATABASE.getComponentsJSONString());
    blocksArea.verifyAllBlocks();
  }

  @Override
  public boolean beforeComponentTypeRemoved(List<String> componentTypes) {
    return true;
  }

  @Override
  public void onComponentTypeRemoved(Map<String, String> componentTypes) {
    blocksArea.populateComponentTypes(COMPONENT_DATABASE.getComponentsJSONString());
    blocksArea.verifyAllBlocks();
  }

  @Override
  public void onResetDatabase() {
    blocksArea.populateComponentTypes(COMPONENT_DATABASE.getComponentsJSONString());
    blocksArea.verifyAllBlocks();
  }

  @Override
  public void makeActiveWorkspace() {
    blocksArea.makeActive();
  }

  public static native void resendAssetsAndExtensions()/*-{
    if (top.ReplState && top.ReplState.state == Blockly.ReplMgr.rsState.CONNECTED) {
      Blockly.ReplMgr.resendAssetsAndExtensions();
    }
  }-*/;

  public static native void resendExtensionsList()/*-{
    if (top.ReplState && top.ReplState.state == Blockly.ReplMgr.rsState.CONNECTED) {
      Blockly.ReplMgr.loadExtensions();
    }
  }-*/;
}

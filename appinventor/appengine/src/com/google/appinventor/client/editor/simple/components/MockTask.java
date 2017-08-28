// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.simple.components;


import java.util.HashSet;

import com.google.appinventor.client.editor.simple.SimpleEditor;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.TreeItem;

/**
 * Mock Task component.
 *
 */
public final class MockTask extends MockContext {

  /**
   * Component type name.
   */
  public static final String TYPE = "Task";

  private static final String VISIBLE_TYPE = "Task";

  // Task UI components
  private MockComponent selectedComponent;


  // Set of listeners for any changes of the task
  final HashSet<ContextChangeListener> taskChangeListeners = new HashSet<ContextChangeListener>();

  // TODO (jusrkg) : Deal with this
  private MockFormLayout myLayout;

  // flag to control attempting to enable/disable vertical
  // alignment when scrollable property is changed
  private boolean initialized = false;

  /**
   * Creates a new MockTask component.
   *
   * @param editor  editor of source file the component belongs to
   */
  public MockTask(SimpleEditor editor) {
    super(editor, TYPE, images.form(), MockFormHelper.makeLayout());
    AbsolutePanel taskWidget = new AbsolutePanel();
    taskWidget.setStylePrimaryName("ode-SimpleMockForm");
    initComponent(taskWidget);
    initialized = true;
  }


  @Override
  public final MockForm getForm() {
    return null;
  }

  @Override
  public final MockTask getTask() {
    return this;
  }

  @Override
  public final boolean isForm() {
    return false;
  }

  @Override
  public final boolean isTask() {
    return true;
  }

  @Override
  public String getVisibleTypeName() {
    return VISIBLE_TYPE;
  }


  /**
   * Forces a re-layout of the child components of the container.
   */
  public final void refresh() {
  }

  /**
   * Forces a re-layout of the child components of the container.
   */
  public final void doRefresh() {
  }

  /**
   * Adds an {@link ContextChangeListener} to the listener set if it isn't already in there.
   *
   * @param listener  the {@code ContextChangeListener} to be added
   */
  public void addContextChangeListener(ContextChangeListener listener) {
    taskChangeListeners.add(listener);
  }

  /**
   * Removes an {@link ContextChangeListener} from the listener list.
   *
   * @param listener  the {@code ContextChangeListener} to be removed
   */
  public void removeContextChangeListener(ContextChangeListener listener) {
    taskChangeListeners.remove(listener);
  }

  /**
   * Triggers a component property change event to be sent to the listener on the listener list.
   */
  protected void fireComponentPropertyChanged(MockComponent component,
                                              String propertyName, String propertyValue) {
    for (ContextChangeListener listener : taskChangeListeners) {
      listener.onComponentPropertyChanged(component, propertyName, propertyValue);
    }
  }

  /**
   * Triggers a component removed event to be sent to the listener on the listener list.
   */
  protected void fireComponentRemoved(MockComponent component, boolean permanentlyDeleted) {
    for (ContextChangeListener listener : taskChangeListeners) {
      listener.onComponentRemoved(component, permanentlyDeleted);
    }
  }

  /**
   * Triggers a component added event to be sent to the listener on the listener list.
   */
  protected void fireComponentAdded(MockComponent component) {
    for (ContextChangeListener listener : taskChangeListeners) {
      listener.onComponentAdded(component);
    }
  }

  /**
   * Triggers a component renamed event to be sent to the listener on the listener list.
   */
  protected void fireComponentRenamed(MockComponent component, String oldName) {
    for (ContextChangeListener listener : taskChangeListeners) {
      listener.onComponentRenamed(component, oldName);
    }
  }

  /**
   * Triggers a component selection change event to be sent to the listener on the listener list.
   */
  protected void fireComponentSelectionChange(MockComponent component, boolean selected) {
    for (ContextChangeListener listener : taskChangeListeners) {
      listener.onComponentSelectionChange(component, selected);
    }
  }

  /**
   * Changes the component that is currently selected in the task.
   * <p>
   * There will always be exactly one component selected in a task
   * at any given time.
   */
  public final void setSelectedComponent(MockComponent newSelectedComponent) {
    MockComponent oldSelectedComponent = selectedComponent;

    if (newSelectedComponent == null) {
      throw new IllegalArgumentException("at least one component must always be selected");
    }
    if (newSelectedComponent == oldSelectedComponent) {
      return;
    }

    selectedComponent = newSelectedComponent;

    if (oldSelectedComponent != null) {     // Can be null initially
      oldSelectedComponent.onSelectedChange(false);
    }
    newSelectedComponent.onSelectedChange(true);
  }

  public final MockComponent getSelectedComponent() {
    return selectedComponent;
  }

  /**
   * Builds a tree of the component hierarchy of the task for display in the
   * {@code SourceStructureExplorer}.
   *
   * @return  tree showing the component hierarchy of the task
   */
  public TreeItem buildComponentsTree() {
    return buildTree();
  }
}

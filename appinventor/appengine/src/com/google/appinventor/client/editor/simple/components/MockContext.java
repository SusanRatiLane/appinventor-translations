// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.simple.components;

import com.google.appinventor.client.editor.simple.SimpleEditor;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.TreeItem;

public abstract class MockContext extends MockContainer {

  MockContext(SimpleEditor editor, String type, ImageResource icon,
                        MockLayout layout) {
    super(editor, type, icon, layout);
  }

  @Override
  public final MockContext getContext() {
    return this;
  }

  @Override
  public MockForm getForm() {
    return null;
  }

  @Override
  public MockTask getTask() {
    return null;
  }

  @Override
  public final boolean isContext() {
    return true;
  }

  @Override
  public boolean isForm() {
    return false;
  }

  @Override
  public boolean isTask() {
    return false;
  }

  public void refresh(){}

  public void doRefresh(){}

  /**
   * Adds an {@link ContextChangeListener} to the listener set if it isn't already in there.
   *
   * @param listener  the {@code ContextChangeListener} to be added
   */
  public void addContextChangeListener(ContextChangeListener listener) {}

  /**
   * Removes an {@link ContextChangeListener} from the listener list.
   *
   * @param listener  the {@code ContextChangeListener} to be removed
   */
  public void removeContextChangeListener(ContextChangeListener listener) {}

  /**
   * Triggers a component property change event to be sent to the listener on the listener list.
   */
  protected void fireComponentPropertyChanged(MockComponent component,
                                              String propertyName, String propertyValue) {}

  /**
   * Triggers a component removed event to be sent to the listener on the listener list.
   */
  protected void fireComponentRemoved(MockComponent component, boolean permanentlyDeleted) {}

  /**
   * Triggers a component added event to be sent to the listener on the listener list.
   */
  protected void fireComponentAdded(MockComponent component) {}

  /**
   * Triggers a component renamed event to be sent to the listener on the listener list.
   */
  protected void fireComponentRenamed(MockComponent component, String oldName) {}

  /**
   * Triggers a component selection change event to be sent to the listener on the listener list.
   */
  protected void fireComponentSelectionChange(MockComponent component, boolean selected) {}

  /**
   * Changes the component that is currently selected in the context.
   * <p>
   * There will always be exactly one component selected in a context
   * at any given time.
   */
  public void setSelectedComponent(MockComponent newSelectedComponent) {}

  public MockComponent getSelectedComponent() {
    return null;
  }

  /**
   * Builds a tree of the component hierarchy of the context for display in the
   * {@code SourceStructureExplorer}.
   *
   * @return  tree showing the component hierarchy of the context
   */
  public TreeItem buildComponentsTree() {
    return null;
  }

}

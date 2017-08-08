// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.content.Context;
import com.google.appinventor.components.annotations.SimpleObject;

/**
 * Base class for all non-visible components.
 *
 * @author lizlooney@google.com (Liz Looney)
 */
@SimpleObject
public abstract class AndroidNonvisibleComponent implements Component {

  protected final ComponentContainer container;
  protected final Context context;
  protected final Form form;
  protected final Task task;

  /**
   * Creates a new AndroidNonvisibleComponent.
   *
   * @param container the container that this component will be placed in
   */
  protected AndroidNonvisibleComponent(ComponentContainer container) {
    this.container = container;
    this.context = container.$context();
    this.form = container.$form();
    this.task = container.$task();
  }

  // Component implementation

  @Override
  public HandlesEventDispatching getDispatchDelegate() {
    if (form != null)
      return form.getDispatchDelegate();
    if (task != null)
      return task.getDispatchDelegate();
    return null;
  }
}

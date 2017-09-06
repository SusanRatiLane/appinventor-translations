// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.content.Context;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.runtime.util.ErrorMessages;

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

  protected final boolean isTaskCompatible;
  protected final boolean isScreenCompatible;

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

    // we set our compatibility variables from annotations
    SimpleObject simpleObject = this.getClass().getAnnotation(SimpleObject.class);
    isScreenCompatible = simpleObject.screenCompatible();
    isTaskCompatible = simpleObject.taskCompatible();

    notifyIfUnsupportedInContext();

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

  protected void notifyIfUnsupportedInContext() {
    if (!isScreenCompatible && container.inForm()) {
      container.dispatchErrorOccurredEvent(this, "Initialize",
              ErrorMessages.ERROR_COMPONENT_UNSUPPORTED_IN_FORM,
              this.getClass().getSimpleName());
    }
    if (!isTaskCompatible && container.inTask()) {
      container.dispatchErrorOccurredEvent(this, "Initialize",
              ErrorMessages.ERROR_COMPONENT_UNSUPPORTED_IN_TASK,
              this.getClass().getSimpleName());
    }
  }

  protected void notifyUnsupportedMethodInContext(String methodName) {
    if (container.inForm()) {
      container.dispatchErrorOccurredEvent(this, methodName,
              ErrorMessages.ERROR_COMPONENT_METHOD_UNSUPPORTED_IN_FORM,
              methodName, this.getClass().getSimpleName());
    }
    if (container.inTask()) {
      container.dispatchErrorOccurredEvent(this, methodName,
              ErrorMessages.ERROR_COMPONENT_METHOD_UNSUPPORTED_IN_TASK,
              methodName, this.getClass().getSimpleName());
    }
  }

  protected void notifyUnsupportedFeatureInContext(String methodName, String featureName) {
    if (container.inForm()) {
      container.dispatchErrorOccurredEvent(this, methodName,
              ErrorMessages.ERROR_COMPONENT_METHOD_UNSUPPORTED_IN_FORM,
              featureName, this.getClass().getSimpleName());
    }
    if (container.inTask()) {
      container.dispatchErrorOccurredEvent(this, methodName,
              ErrorMessages.ERROR_COMPONENT_METHOD_UNSUPPORTED_IN_TASK,
              featureName, this.getClass().getSimpleName());
    }
  }

}

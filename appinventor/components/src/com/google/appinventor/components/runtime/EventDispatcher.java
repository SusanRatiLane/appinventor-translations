// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dispatches events to component event handlers.
 *
 * @author markf@google.com (Mark Friedman)
 * @author lizlooney@google.com (Liz Looney)
 */
public class EventDispatcher {

  private static final class EventClosure {
    private final String componentName; // componentName
    private final String eventName;

    private EventClosure(String componentName, String eventName) {
      this.componentName = componentName;
      this.eventName = eventName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      EventClosure that = (EventClosure) o;

      if (!componentName.equals(that.componentName)) {
        return false;
      }
      if (!eventName.equals(that.eventName)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * eventName.hashCode() + componentName.hashCode();
    }
  }

  /*
   * Each EventRegistry is associated with one context.
   * It contains all the event closures for a single form/task.
   */
  private static final class EventRegistry {
    private final String context;

    // Mapping of event names to a set of event closures.
    // Note that by using a Set here, we'll only have one closure corresponding to a
    // given componentId-eventName.  We do not support invoking multiple handlers for a
    // single event.
    private final HashMap<String, Set<EventClosure>> eventClosuresMap =
        new HashMap<String, Set<EventClosure>>();

    EventRegistry(String contextName) {
      this.context = contextName;
    }
  }

  private static final boolean DEBUG = true;

  private static final Map<String, EventRegistry>
      mapContextToEventRegistry = new HashMap<String, EventRegistry>();

  private EventDispatcher() {
  }

  private static EventRegistry getEventRegistry(String context) {
    EventRegistry er = mapContextToEventRegistry.get(context);
    if (er == null) {
      er = new EventRegistry(context);
      mapContextToEventRegistry.put(context, er);
    }
    return er;
  }

  private static EventRegistry removeEventRegistry(String context) {
    return mapContextToEventRegistry.remove(context);
  }


  /**
   * Registers a dispatchDelegate for handling event dispatching for the event with the specified
   * component id and event name.
   *
   * @param context  name of context
   * @param componentName  name of component associated with event handler
   * @param eventName  name of event
   */
  // Don't delete this method. It's called from runtime.scm.
  public static void registerEventForDelegation(String context,
                                                String componentName, String eventName) {
    EventRegistry er = getEventRegistry(context);
    Set<EventClosure> eventClosures = er.eventClosuresMap.get(eventName);
    if (eventClosures == null) {
      eventClosures = new HashSet<EventClosure>();
      er.eventClosuresMap.put(eventName, eventClosures);
    }

    eventClosures.add(new EventClosure(componentName, eventName));
    if (DEBUG) {
      Log.i("EventDispatcher", "Registered event closure for " +
          componentName + "." + eventName);
    }
  }

  /**
   * Unregisters a dispatchDelegate for handling event dispatching for the event with the specified
   * component id and event name.
   *
   * @param context  name of context
   * @param componentName  name of component associated with event handler
   * @param eventName  name of event
   */
  // Don't delete this method. It's called from runtime.scm.
  public static void unregisterEventForDelegation(String context,
                                                  String componentName, String eventName) {
    EventRegistry er = getEventRegistry(context);
    Set<EventClosure> eventClosures = er.eventClosuresMap.get(eventName);
    if (eventClosures == null || eventClosures.isEmpty()) {
      return;
    }
    Set<EventClosure> toDelete = new HashSet<EventClosure>();
    for (EventClosure eventClosure : eventClosures) {
      if (eventClosure.componentName.equals(componentName)) {
        toDelete.add(eventClosure);
      }
    }
    for (EventClosure eventClosure : toDelete) {
      if (DEBUG) {
        Log.i("EventDispatcher", "Deleting event closure for " +
            eventClosure.componentName + "." + eventClosure.eventName);
      }
      eventClosures.remove(eventClosure);
    }
  }

  /**
   * Removes all event closures previously registered via
   * {@link EventDispatcher#registerEventForDelegation}.
   */
  // Don't delete this method. It's called from runtime.scm.
  public static void unregisterAllEventsForDelegation() {
    Log.i("EventDispatcher", "unregisterAllEventsForDelegation");
    for (EventRegistry er : mapContextToEventRegistry.values()) {
      er.eventClosuresMap.clear();
    }
  }

  /**
   * Removes all event closures previously registered via
   * {@link EventDispatcher#registerEventForDelegation}.
   */
  // Don't delete this method. It's called from runtime.scm.
  public static void unregisterAllEventsOfContext(String contextName) {
    Log.i("EventDispatcher", "unregisterAllEventsOfContext : " + contextName);
    EventRegistry er = mapContextToEventRegistry.get(contextName);
    if (er != null) {
      er.eventClosuresMap.clear();
    }
  }

  /**
   * Removes event handlers previously registered with the given
   * context and clears all references to the dispatchDelegate in
   * this class.
   *
   * Called when a Form's onDestroy method is called.
   */
  public static void removeDispatchContext(String context) {
    EventRegistry er = removeEventRegistry(context);
    if (er != null) {
      er.eventClosuresMap.clear();
    }
  }

  /**
   * Dispatches an event based on its name to any registered handlers.
   *
   * @param component  the component raising the event
   * @param eventName  name of event being raised
   * @param args  arguments to the event handler
   */
  public static boolean dispatchEvent(Component component, String eventName, Object...args) {
    if (DEBUG) {
      Log.i("EventDispatcher", "Trying to dispatch event " + eventName);
    }
    boolean dispatched = false;
    HandlesEventDispatching dispatchDelegate = component.getDispatchDelegate();

    Log.i("EventDispatcher", "DispatchDelegate is " + dispatchDelegate.toString());
    if (dispatchDelegate.canDispatchEvent(component, eventName)) {
      Log.i("EventDispatcher", "DispatchDelegate can dispatch " + eventName);
      EventRegistry er = getEventRegistry(dispatchDelegate.getDispatchContext());
      Set<EventClosure> eventClosures = er.eventClosuresMap.get(eventName);
      Log.d("EventDispatcher", "about to delegateDispatchEvent: " + er+ " closures: " + eventClosures);
      if (eventClosures != null && eventClosures.size() > 0) {
        dispatched = delegateDispatchEvent(dispatchDelegate, eventClosures, component, args);
      }
    }
    return dispatched;
  }

  /**
   * Delegates the dispatch of an event to the dispatch delegate.
   *
   * @param eventClosures set of event closures matching the event name
   * @param component the component that generated the event
   * @param args  arguments to event handler
   */
  private static boolean delegateDispatchEvent(HandlesEventDispatching dispatchDelegate,
                                               Set<EventClosure> eventClosures,
                                               Component component, Object... args) {
    // The event closures set will contain all event closures matching the event name.
    // We depend on the delegate's dispatchEvent method to check the registered event closure and
    // only dispatch the event if the registered component matches the component that generated the
    // event.  This should only be true for one (or zero) of the closures.
    boolean dispatched = false;
    Log.d("EventDispatcher", "delegateDispatchEvent called : " + dispatchDelegate + " thread:" + Thread.currentThread());
    for (EventClosure eventClosure : eventClosures) {
      Log.d("EventDispatcher", "event Closure hit: " + eventClosure.componentName + "." + eventClosure.eventName);
      if (dispatchDelegate.dispatchEvent(component,
                                         eventClosure.componentName,
                                         eventClosure.eventName,
                                         args)) {
        if (DEBUG) {
          Log.i("EventDispatcher", "Successfully dispatched event " +
              eventClosure.componentName + "." + eventClosure.eventName);
        }
        dispatched = true;  // break here or keep iterating through loop?
      }
    }
    return dispatched;
  }

  // Don't delete this method. It's called from runtime.scm.
  public static String makeFullEventName(String componentName, String eventName) {
    if (DEBUG) {
      Log.i("EventDispatcher", "makeFullEventName componentId=" + componentName + ", " +
          "eventName=" + eventName);
    }
    return componentName + '$' + eventName;
  }
}

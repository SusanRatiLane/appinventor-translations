// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import android.support.v4.content.LocalBroadcastManager;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.collect.Sets;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.OnInitializeListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Component underlying services, not directly accessible to Simple programmers.
 *
 * <p>This is the root container of any Android Service.
 *
 */
@DesignerComponent(version = YaVersion.TASK_COMPONENT_VERSION,
    category = ComponentCategory.INTERNAL,
    description = "Top-level component containing all other components in a task",
    showOnPalette = false)
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET,android.permission.ACCESS_WIFI_STATE,android.permission.ACCESS_NETWORK_STATE")
public class Task extends Service
    implements Component, ComponentContainer, HandlesEventDispatching {

  private static final String LOG_TAG = "Task";

  public static final String LOCAL_ACTION_SEND_MESSAGE = "TaskSendMessage";
  public static final String LOCAL_ACTION_SEND_MESSAGE_PARAM_TASK_NAME = "Task";
  public static final String LOCAL_ACTION_SEND_MESSAGE_PARAM_TITLE = "Title";
  public static final String LOCAL_ACTION_SEND_MESSAGE_PARAM_MESSAGE = "Message";

  protected static Map<String, Task> taskMap = new HashMap<String, Task>();

  protected String taskName;
  protected int taskType;


  public static class TaskThread extends HandlerThread {

    protected Handler taskHandler;
    protected final Task task;

    public TaskThread(String taskName, Task task) {
      super(taskName);
      this.task = task;
      this.start();
    }

    @Override
    public void start() {
      super.start();
      prepareHandler(); // Prepares our Handler for communication
    }

    public void start(Handler taskHandler) {
      super.start();
      prepareHandler(taskHandler);
    }

    protected void prepareHandler() {
      taskHandler = new Handler(getLooper());
    }

    protected void prepareHandler(Handler taskHandler) {
      this.taskHandler = taskHandler;
    }


    public Handler getTaskHandler() {
      return taskHandler;
    }

    public Task getTask() {
      return task;
    }
  }

  private TaskThread taskThread;

  private LocalBroadcastManager localBroadcastManager;

  // Application lifecycle related fields
  private final Set<OnDestroyListener> onDestroyListeners = Sets.newHashSet();

  // AppInventor lifecycle: listeners for the Initialize Event
  private final Set<OnInitializeListener> onInitializeListeners = Sets.newHashSet();

  private boolean taskInitialized = false;

  private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      String taskName = intent.getStringExtra(Form.LOCAL_ACTION_SEND_MESSAGE_PARAM_TASK_NAME);
      String formName = intent.getStringExtra(Form.LOCAL_ACTION_SEND_MESSAGE_PARAM_FORM_NAME);
      String title = intent.getStringExtra(Form.LOCAL_ACTION_SEND_MESSAGE_PARAM_TITLE);
      String stringMessage = intent.getStringExtra(Form.LOCAL_ACTION_SEND_MESSAGE_PARAM_MESSAGE);
      Log.d(LOG_TAG,"Received from Form : form : " + formName + " taskName : " + taskName + " title: " + title + " message: " + stringMessage);
      Object message = Form.decodeJSONStringForForm(stringMessage, "receive from task");
      triggerReceivedFromScreen(taskName, title, message);

    }
  };

  @Override
  public void onCreate() {

    String className = getClass().getName();
    int lastDot = className.lastIndexOf('.');
    taskName = className.substring(lastDot + 1);
    Log.d(LOG_TAG, "Task " + taskName + " got onCreate");

    taskThread = new TaskThread(taskName,this);
    taskMap.put(taskName, this);

    Runnable initialize = new Runnable() {
      @Override
      public void run() {

        $define();

        Initialize();

        // Listen messages from Forms
        LocalBroadcastManager.getInstance(Task.this).registerReceiver(broadcastReceiver, new IntentFilter(Form.LOCAL_ACTION_SEND_MESSAGE));
      }
    };

    taskThread.getTaskHandler().post(initialize);


  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /**
   * An app can register to be notified when App Inventor's Initialize
   * block has fired.  They will be called in Initialize().
   *
   * @param component
   */
  public void registerForOnInitialize(OnInitializeListener component) {
    onInitializeListeners.add(component);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d("Task", "Task onStartCommand Called");
    String startValue = intent.getStringExtra(Form.SERVICE_ARG);
    final Object decodedStartVal = Form.decodeJSONStringForForm(startValue, "get start value");
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        TaskStarted(decodedStartVal);
      }
    };
    taskThread.getTaskHandler().post(runnable);
    Log.d("Task", "Done Dispatch about to Return");
    return START_NOT_STICKY;
  }

  @SimpleEvent(description = "Task has been started")
  public void TaskStarted(Object startValue) {
    EventDispatcher.dispatchEvent(this, "TaskStarted", startValue);
  }

  protected void triggerReceivedFromScreen(String taskName, final String title, final Object message) {
    if (!this.taskName.equals(taskName)) {
      return;
    }
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        ReceivedFromScreen(title, message);
      }
    };
    taskThread.getTaskHandler().post(runnable);
    return;
  }

  @SimpleEvent(description = "Task has received a message from a screen")
  public void ReceivedFromScreen(String title, Object message) {
    EventDispatcher.dispatchEvent(this, "ReceivedFromScreen", title, message);
  }

  /**
   * ErrorOccurred event handler.
   */
  @SimpleEvent(
      description = "Event raised when an error occurs. Only some errors will " +
          "raise this condition.  For those errors, the system will show a notification " +
          "by default.  You can use this event handler to prescribe an error " +
          "behavior different than the default.")
  public void ErrorOccurred(Component component, String functionName, int errorNumber,
                            String message) {
    String componentType = component.getClass().getName();
    componentType = componentType.substring(componentType.lastIndexOf(".") + 1);
    Log.e(LOG_TAG, "Task " + taskName + " ErrorOccurred, errorNumber = " + errorNumber +
        ", componentType = " + componentType + ", functionName = " + functionName +
        ", messages = " + message);
    if (!EventDispatcher.dispatchEvent(
        this, "ErrorOccurred", component, functionName, errorNumber, message)
        && taskInitialized) {
      // If dispatchEvent returned false, then no user-supplied error handler was run.
      // If in addition, the screen initializer was run, then we assume that the
      // user did not provide an error handler.   In this case, we run a default
      // error handler, namely, showing a notification to the end user of the app.
      // The app writer can override this by providing an error handler.
      new Notifier(this).ShowAlert("Error " + errorNumber + ": " + message);
    }
  }

  public void ErrorOccurredDialog(Component component, String functionName, int errorNumber,
                            String message, String title, String buttonText) {
    String componentType = component.getClass().getName();
    componentType = componentType.substring(componentType.lastIndexOf(".") + 1);
    Log.e(LOG_TAG, "Task " + taskName + " ErrorOccurred, errorNumber = " + errorNumber +
            ", componentType = " + componentType + ", functionName = " + functionName +
            ", messages = " + message);
    if (!EventDispatcher.dispatchEvent(
            this, "ErrorOccurred", component, functionName, errorNumber, message)
            && taskInitialized) {
      // If dispatchEvent returned false, then no user-supplied error handler was run.
      // If in addition, the screen initializer was run, then we assume that the
      // user did not provide an error handler.   In this case, we run a default
      // error handler, namely, showing a notification to the end user of the app.
      // The app writer can override this by providing an error handler.
      new Notifier(this).ShowMessageDialog("Error " + errorNumber + ": " + message, title, buttonText);
    }
  }


  public void dispatchErrorOccurredEvent(final Component component, final String functionName,
                                         final int errorNumber, final Object... messageArgs) {
    Log.i(LOG_TAG, "TASK dispatchErrorOccurredEvent");
    String message = ErrorMessages.formatMessage(errorNumber, messageArgs);
    ErrorOccurred(component, functionName, errorNumber, message);
  }

  public void dispatchErrorOccurredEventDialog(final Component component, final String functionName,
                                         final int errorNumber, final Object... messageArgs) {
    Log.i(LOG_TAG, "TASK dispatchErrorOccurredEventDialog");
    String message = ErrorMessages.formatMessage(errorNumber, messageArgs);
    ErrorOccurredDialog(component, functionName, errorNumber, message, "Error in " + functionName, "Dismiss");
  }

  @Override
  public void onDestroy() {
    Log.i(LOG_TAG, "Task " + taskName + " got onDestroy");
    for (OnDestroyListener onDestroyListener : onDestroyListeners) {
      onDestroyListener.onDestroy();
    }
    super.onDestroy();
    taskMap.remove(taskName);
    // for debugging and future growth
    Log.i(LOG_TAG, "Task " + taskName + " got onDestroy");

    LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

    // Unregister events for components in this task.
    EventDispatcher.removeDispatchContext(this.getDispatchContext());
  }

  public void registerForOnDestroy(OnDestroyListener component) {
    onDestroyListeners.add(component);
  }

  /**
   * Compiler-generated method to initialize and add application components to
   * the form.  We just provide an implementation here to artificially make
   * this class concrete so that it is included in the documentation and
   * Codeblocks language definition file generated by
   * {@link com.google.appinventor.components.scripts.DocumentationGenerator} and
   * {@link com.google.appinventor.components.scripts.LangDefXmlGenerator},
   * respectively.  The actual implementation appears in {@code runtime.scm}.
   */
  protected void $define() {    // This must be declared protected because we are called from Task1 which subclasses
    // us and isn't in our package.
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean canDispatchEvent(Component component, String eventName) {
    // Events can only be dispatched after the screen initialized event has completed.
    boolean canDispatch = taskInitialized || (component == this && eventName.equals("Initialize"));
    Log.e(LOG_TAG, "canDispatch " + canDispatch);
    return canDispatch;
  }

  /**
   * A trivial implementation to artificially make this class concrete so
   * that it is included in the documentation and
   * Codeblocks language definition file generated by
   * {@link com.google.appinventor.components.scripts.DocumentationGenerator} and
   * {@link com.google.appinventor.components.scripts.LangDefXmlGenerator},
   * respectively.  The actual implementation appears in {@code runtime.scm}.
   */
  @Override
  public boolean dispatchEvent(Component component, String componentName, String eventName,
                               Object[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDispatchContext() {
    return this.taskName;
  }

  /**
   * Initialize event handler.
   */
  @SimpleEvent(description = "Service starting")
  public void Initialize() {
    EventDispatcher.dispatchEvent(Task.this, "Initialize");
    taskInitialized = true;

    //  Call all apps registered to be notified when Initialize Event is dispatched
    for (OnInitializeListener onInitializeListener : onInitializeListeners) {
      onInitializeListener.onInitialize();
    }
  }

  @SimpleFunction(description = "Send a message to the screen")
  public void SendToScreen(String title, Object message) {
    Log.i(LOG_TAG, "Sending from Task to Screen : " + this.getTaskName() + " title : " + title + " message : " + message.toString());
    Intent intent = new Intent(Task.LOCAL_ACTION_SEND_MESSAGE);
    intent.putExtra(Task.LOCAL_ACTION_SEND_MESSAGE_PARAM_TASK_NAME, this.getTaskName());
    intent.putExtra(Task.LOCAL_ACTION_SEND_MESSAGE_PARAM_TITLE, title);
    intent.putExtra(Task.LOCAL_ACTION_SEND_MESSAGE_PARAM_MESSAGE, message.toString());
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  // Component implementation

  @Override
  public HandlesEventDispatching getDispatchDelegate() {
    return this;
  }

  // ComponentContainer implementation

  @Override
  public Context $context() {
    return this;
  }

  @Override
  public Form $form() {
    return null;
  }

  @Override
  public Task $task() {
    return this;
  }

  @Override
  public boolean isContext() {
    return true;
  }

  @Override
  public boolean isForm() {
    return false;
  }

  @Override
  public boolean isTask() {
    return true;
  }

  @Override
  public boolean inForm() {
    return isForm();
  }

  @Override
  public boolean inTask() {
    return isTask();
  }

  // We don' need these but ComponentContainer implements
  // these so, we'll see what to do

  @Override
  public void $add(AndroidViewComponent component) {
  }

  @Override
  public void setChildWidth(AndroidViewComponent component, int width) {
  }

  @Override
  public void setChildHeight(AndroidViewComponent component, int height) {
  }

  @Override
  public int Width() {
    return 0;
  }

  @Override
  public int Height() {
    return 0;
  }



  /**
   * Specifies the Task Behaviour.
   *
   * @param tType the type of Task
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TASK_TYPE,
      defaultValue = Component.TASK_TYPE_QUICK + "")
  @SimpleProperty(userVisible = true,
      description = "This property determines the basic behaviour of a Task")
  public void TaskType(int tType) {
    taskType = tType;
  }

  /**
   * Returns the Task Behaviour.
   *
   */
  @SimpleProperty(userVisible = true,
      description = "This property determines the basic behaviour of a Task")
  public int TaskType() {
    return taskType;
  }

  /**
   * Specifies the Version Code.
   *
   * @param vCode the version name of the application
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER,
      defaultValue = "1")
  @SimpleProperty(userVisible = false,
      description = "An integer value which must be incremented each time a new Android "
          +  "Application Package File (APK) is created for the Google Play Store.")
  public void VersionCode(int vCode) {
    // We don't actually need to do anything.
  }

  /**
   * Specifies the Version Name.
   *
   * @param vName the version name of the application
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
      defaultValue = "1.0")
  @SimpleProperty(userVisible = false,
      description = "A string which can be changed to allow Google Play "
          + "Store users to distinguish between different versions of the App.")
  public void VersionName(String vName) {
    // We don't actually need to do anything.
  }

  public void deleteComponent(Object component) {
    if (component instanceof OnDestroyListener) {
      OnDestroyListener onDestroyListener = (OnDestroyListener) component;
      if (onDestroyListeners.contains(onDestroyListener)) {
        onDestroyListeners.remove(onDestroyListener);
      }
    }
  }

  // This is used by runtime.scm to call the Initialize of a component.
  public void callInitialize(Object component) throws Throwable {
    Method method;
    try {
      method = component.getClass().getMethod("Initialize", (Class<?>[]) null);
    } catch (SecurityException e) {
      Log.i(LOG_TAG, "Security exception " + e.getMessage());
      return;
    } catch (NoSuchMethodException e) {
      //This is OK.
      return;
    }
    try {
      Log.i(LOG_TAG, "calling Initialize method for Object " + component.toString());
      method.invoke(component, (Object[]) null);
    } catch (InvocationTargetException e){
      Log.i(LOG_TAG, "invoke exception: " + e.getMessage());
      throw e.getTargetException();
    }
  }


  public String getTaskName() {
    return taskName;
  }

  // This is called by runtime.scm
  /**
   * Returns the Task instance corresponding to Task Name
   * @param taskName
   */
  public static Task getTask(String taskName) {
    return taskMap.get(taskName);
  }

  // This is called by runtime.scm
  /**
   * Returns the Task instance of the Thread this method is called on.
   * @return
   */
  public static Task getCurrentTask() {
    Thread thread = Thread.currentThread();
    if (!(thread instanceof TaskThread)) {
      // We are not called on a TaskThread
      return null;
    }
    if (thread instanceof ReplTask.ReplTaskThread) {
      return ReplTask.replTask;
    }
    String taskName = thread.getName();
    Task currentTask = taskMap.get(taskName);
    if (currentTask == null) {
      Log.d(LOG_TAG, "There is no task for TaskThread : " + taskName );
      throw new IllegalThreadStateException("There is No Task for the TaskThread : " + taskName);
    }
    return currentTask;
  }

  // This is called by runtime.scm
  /**
   * Returns the Task name of the Thread this method is called on.
   * @return
   */
  public static String getCurrentTaskName() {
    Task currentTask = Task.getCurrentTask();
    return currentTask.getTaskName();
  }


}

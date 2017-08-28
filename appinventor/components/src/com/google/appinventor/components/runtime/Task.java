// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
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
import com.google.appinventor.components.runtime.util.EclairUtil;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.OnInitializeListener;
import com.google.appinventor.components.runtime.util.SdkLevel;

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

  protected static int taskNotificationCounter = 0;
  protected static Map<String, Task> taskMap = new HashMap<String, Task>();

  public static class TaskThread extends HandlerThread {

    // We enclose task specific properties in a TaskThread so that we can
    // run and manage multiple TaskThreads in ReplTask essentially mimicking
    // multiple Tasks within a single Service.
    protected Handler taskHandler;

    protected String taskName;
    protected int taskType = TASK_TYPE_SCREEN;

    protected TaskNotification notification;

    // Application lifecycle related fields
    protected final Set<OnDestroyListener> onDestroyListeners = Sets.newHashSet();

    // AppInventor lifecycle: listeners for the Initialize Event
    protected final Set<OnInitializeListener> onInitializeListeners = Sets.newHashSet();
    protected final Set<OnStopListener> onStopListeners = Sets.newHashSet();

    protected boolean taskInitialized = false;

    public TaskThread(String taskName, Task task) {
      super(taskName);
      this.taskName = taskName;
      notification = new TaskNotification(taskName, task);
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

    public String getTaskName() {
      return taskName;
    }

    public void setTaskType(int type) {
      this.taskType = type;
    }

    public int getTaskType() {
      return taskType;
    }

    public void setTaskInitiliazed(boolean initiliazed) {
      this.taskInitialized = initiliazed;
    }

    public boolean getTaskInitiliazed() {
      return taskInitialized;
    }

    public TaskNotification getTaskNotification() {
      return notification;
    }

    public Set<OnDestroyListener> getOnDestroyListeners() {
      return onDestroyListeners;
    }

    public Set<OnInitializeListener> getOnInitializeListeners() {
      return onInitializeListeners;
    }

    public Set<OnStopListener> getOnStopListeners() {
      return onStopListeners;
    }

  }

  /**
   * TaskNotification handles notifications for each task.
   * A TaskNotification is compulsory for Tasks of type TASK_TYPE_STICKY and are automatically set.
   *
   * Some methods of this class are synchronized because they are used across multiple threads namely
   * the Main thread (for onCreate, onStart, onDestroy) and the Task thread.
   *
   */
  protected static class TaskNotification {

    protected static final String DEFAULT_CONTENT_TEXT = "In progress";
    protected static final int ID_BASE_ADD = 10000;
    protected static final int DEFAULT_CONTENT_INTENT_REQUEST_CODE = 0;


    protected final int Id;
    protected final int icon;
    protected final String defaultContentTitle;
    protected String contentTitle;
    protected String contentText;
    protected PendingIntent contentIntent;
    protected NotificationCompat.Builder notificationBuilder;
    protected NotificationManager notificationManager;

    private Notification notification;
    private boolean showing = false;

    public TaskNotification(String taskName, Task task) {
      taskNotificationCounter++;
      Id = ID_BASE_ADD + taskNotificationCounter;
      icon = task.getApplicationInfo().icon;
      defaultContentTitle = taskName;
      contentTitle = taskName;
      contentText = DEFAULT_CONTENT_TEXT;
      notificationBuilder = new NotificationCompat.Builder(task);
      notificationBuilder.setSmallIcon(icon);
      notificationBuilder.setContentTitle(contentTitle);
      notificationBuilder.setContentText(contentText);
      notificationBuilder.setContentIntent(getDefaultContentIntent(task));
      notificationBuilder.setOngoing(true);
      notification = notificationBuilder.build();
      notificationManager = (NotificationManager) task.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public int getId() {
      return Id;
    }

    public void setContentTitle(String title) {
      title = title.trim();
      if (title.equals("")) {
        return;
      }
      synchronized (this) {
        contentTitle = title;
        notificationBuilder.setContentTitle(contentTitle);
      }
    }

    public synchronized String getContentTitle() {
      return contentTitle;
    }

    public void setContentText(String text) {
      text = text.trim();
      if (text.equals("")) {
        return;
      }
      synchronized (this) {
        contentText = text;
        notificationBuilder.setContentText(contentText);
      }
    }

    public synchronized String getContentText() {
      return contentText;
    }

    public synchronized void setContentIntent(PendingIntent intent) {
      contentIntent = intent;
      notificationBuilder.setContentIntent(contentIntent);
    }

    public synchronized PendingIntent getContentIntent() {
      return contentIntent;
    }

    public synchronized Notification getNotification() {
      return notificationBuilder.build();
    }

    public synchronized void showNotification() {
      showing = true;
      updateNotification();
    }

    public synchronized void updateNotification() {
      if (!showing) return;
      notification = notificationBuilder.build();
      notificationManager.notify(Id, notification);
    }

    public synchronized void hideNotification() {
      notificationManager.cancel(Id);
      showing = false;
    }

    public synchronized void setShowing(boolean showing) {
      this.showing = showing;
    }

    public synchronized boolean isShowing() {
      return showing;
    }

    public synchronized void reset() {
      hideNotification();
      setContentTitle(defaultContentTitle);
      setContentText(DEFAULT_CONTENT_TEXT);
    }

    protected synchronized PendingIntent getDefaultContentIntent(Task task) {
      Intent screen1Intent = new Intent();
      screen1Intent.setClassName(task, task.getPackageName() + "." + "Screen1");
      return PendingIntent.getActivity(task, DEFAULT_CONTENT_INTENT_REQUEST_CODE,
              screen1Intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

  }

  private TaskThread taskThread;

  // To control volume of error complaints
  private static long minimumToastWait = 10000000000L; // 10 seconds
  private long lastToastTime = System.nanoTime() - minimumToastWait;

  protected static BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
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
    String taskName = className.substring(lastDot + 1);
    taskThread = new TaskThread(taskName, this);
    if (taskMap.keySet().size() == 0) {
      // We are the first task in our App so let us register to listen messages from Forms
      LocalBroadcastManager.getInstance(Task.this).registerReceiver(broadcastReceiver, new IntentFilter(Form.LOCAL_ACTION_SEND_MESSAGE));
    }
    taskMap.put(taskName, this);

    Log.d(LOG_TAG, "Task " + getTaskName() + " got onCreate");

    Runnable initialize = new Runnable() {
      @Override
      public void run() {
        $define();
        Initialize();
      }
    };
    runOnTaskThread(this, initialize);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /**
   * Initialize event handler.
   */
  @SimpleEvent(description = "Service starting")
  public void Initialize() {
    EventDispatcher.dispatchEvent(this, "Initialize");
    setTaskInitiliazed(true);

    //  Call all apps registered to be notified when Initialize Event is dispatched
    Set<OnInitializeListener> onInitializeListeners = getOnInitiliazeListeners();
    for (OnInitializeListener onInitializeListener : onInitializeListeners) {
      onInitializeListener.onInitialize();
    }
  }

  /**
   * An app can register to be notified when App Inventor's Initialize
   * block has fired.  They will be called in Initialize().
   *
   * @param component
   */
  public void registerForOnInitialize(OnInitializeListener component) {
    getOnInitiliazeListeners().add(component);
  }

  @Override
  public void onStart(Intent intent, int startId) {
    // We will be called only if Sdk.getLevel() < Sdk.LEVEL_ECLAIR
    onStartTask(intent, startId);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // We will be called only if API Level >= SdkLevel.LEVEL_ECLAIR
    Log.d("Task", "Task onStartCommand Called");
    onStartTask(intent, startId);
    int returnConstant = START_NOT_STICKY;
    if (this.getTaskType() == Component.TASK_TYPE_SCREEN) {
      returnConstant = START_REDELIVER_INTENT;  // Restart us with the last intent if we are killed.
    } else if (this.getTaskType() == Component.TASK_TYPE_STICKY) {
      returnConstant = START_REDELIVER_INTENT;  // If we are killed restart us with last intent. We are less likely
                                                // to be killed because we are foreground. Theoretically we can be
                                                // killed though.
    } else if (this.getTaskType() == Component.TASK_TYPE_REPEATING) {
      returnConstant = START_NOT_STICKY;        // It is okay if we are killed, we will probably get called again.
    }
    return returnConstant;
  }

  protected void onStartTask(Intent intent, int startId) {
    // Go foreground if we are sticky and supported
    if (this.getTaskType() == TASK_TYPE_STICKY) {
      TaskNotification notification = getNotification();
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_ECLAIR) {
        EclairUtil.startForegroundTask(this, notification.getId(),
                notification.getNotification());
        notification.setShowing(true);
      } else {
        // We don't have foreground service for API SdkLevel.LEVEL_DONUT, we mimic the behaviour (visually)
        notification.showNotification();
      }
    }
    String startValue = intent.getStringExtra(Form.SERVICE_ARG);
    final Object decodedStartVal = Form.decodeJSONStringForForm(startValue, "get start value");
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        TaskStarted(decodedStartVal);
      }
    };
    taskThread.getTaskHandler().post(runnable);
  }

  @SimpleEvent(description = "Task has been started")
  public void TaskStarted(Object startValue) {
    EventDispatcher.dispatchEvent(this, "TaskStarted", startValue);
  }

  /**
   * This is called when our Form closes, we need this to stop all our Screen Tasks
   */
  public static void onFormStop() {
    for (String taskName : taskMap.keySet()) {
      final Task task = taskMap.get(taskName);
      if (task != null) {
        Runnable onStop = new Runnable() {
          @Override
          public void run() {
            task.doStop();
          }
        };
        // We stop ourselves if we are ReplTask
        // No testing tasks must be persistent after the companion is closed
        if (task instanceof ReplTask) {
          Task.runOnTaskThread(taskName, onStop);
          continue;
        }
        if (task.getTaskType() == TASK_TYPE_SCREEN) {
          Task.runOnTaskThread(taskName, onStop);
        }
      }
    }
  }

  protected void doStop() {
    Log.d(LOG_TAG, "Task " + getTaskName() + " got doStop");
    // Invoke all onStopListeners
    Set<OnStopListener> onStopListeners = getOnStopListeners();
    for (OnStopListener onStopListener : onStopListeners) {
      onStopListener.onStop();
    }
    this.onStop();
    stopSelf();
  }

  // Note(justus) : Apparently it seemed to me that the system does not
  // destroy us when we stop, so we add a new fake onStop lifecycle
  // callback and call it on our own.
  public void onStop() {
    getNotification().hideNotification();
  }

  @Override
  public void onDestroy() {
    Log.i(LOG_TAG, "Task " + getTaskName() + " got onDestroy");
    Set<OnDestroyListener> onDestroyListeners = getOnDestroyListeners();
    for (OnDestroyListener onDestroyListener : onDestroyListeners) {
      onDestroyListener.onDestroy();
    }
    super.onDestroy();
    taskMap.remove(getTaskName());
    if (taskMap.keySet().size() == 0) {
      // We are the last Task so unregister local broadcastReceiver
      LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }
    // Unregister events for components in this task.
    EventDispatcher.removeDispatchContext(this.getDispatchContext());
  }

  public void registerForOnStop(OnStopListener component) {
    getOnStopListeners().add(component);
  }

  public void registerForOnDestroy(OnDestroyListener component) {
    getOnDestroyListeners().add(component);
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TEXT)
  @SimpleProperty
  public void NotificationTitle(String title) {
    TaskNotification notification = getNotification();
    notification.setContentTitle(title);
    notification.updateNotification();
  }

  @SimpleProperty(userVisible = true,
          description = "When a Task is Sticky it shows a notification to the user. "
                  + "This property sets the title for that notification ")
  public String NotificationTitle() {
    return getNotification().getContentTitle();
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TEXT,
          defaultValue = TaskNotification.DEFAULT_CONTENT_TEXT)
  @SimpleProperty
  public void NotificationText(String text) {
    TaskNotification notification = getNotification();
    notification.setContentText(text);
    notification.updateNotification();
  }

  @SimpleProperty(userVisible = true,
          description = "When a Task is Sticky it shows a notification to the user. "
                  + "This property sets the title for that notification ")
  public String NotificationText() {
    return getNotification().getContentText();
  }

  /**
   * Specifies the Task Behaviour.
   *
   * @param tType the type of Task
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TASK_TYPE,
          defaultValue = Component.TASK_TYPE_SCREEN + "")
  @SimpleProperty(userVisible = false,
          description = "This property determines the basic behaviour of a Task")
  public void TaskType(int tType) {
    setTaskType(tType);
  }

  /**
   * Returns the Task Behaviour.
   *
   */
  @SimpleProperty(userVisible = true,
          description = "This property determines the basic behaviour of a Task")
  public int TaskType() {
    return getTaskType();
  }


  public void deleteComponent(Object component) {
    if (component instanceof OnDestroyListener) {
      OnDestroyListener onDestroyListener = (OnDestroyListener) component;
      Set<OnDestroyListener> onDestroyListeners = getOnDestroyListeners();
      if (onDestroyListeners.contains(onDestroyListener)) {
        onDestroyListeners.remove(onDestroyListener);
      }
    }
  }

  // This is used by runtime.scm to call the Initialize of a component.
  public static void callInitialize(Object component) throws Throwable {
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


  protected static void triggerReceivedFromScreen(String taskName, final String title, final Object message) {
    final Task task = taskMap.get(taskName);
    if (task == null) {
      // This happens when we were given a message without starting us.
      // We should warn the user about this and gracefully decline.
      Form activeForm = Form.getActiveForm();
      if (activeForm != null) {
        activeForm.dispatchErrorOccurredEvent(activeForm, "SendToTask", ErrorMessages.ERROR_SENDING_MESSAGE_TO_UNAVAILABLE_TASK, taskName );
      }
      return;
    }
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        task.ReceivedFromScreen(title, message);
      }
    };
    Task.runOnTaskThread(task, runnable);
    return;
  }

  @SimpleEvent(description = "Task has received a message from a screen")
  public void ReceivedFromScreen(String title, Object message) {
    EventDispatcher.dispatchEvent(this, "ReceivedFromScreen", title, message);
  }



  /**
   * Compiler-generated method to initialize and add application components to
   * the task.  We just provide an implementation here to artificially make
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

  /**
   * Exclusively for ReplTasks
   * Compiler-generated method to initialize and add application components to
   * the task.  We just provide an implementation here to artificially make
   * this class concrete so that it is included in the documentation and
   * Codeblocks language definition file generated by
   * {@link com.google.appinventor.components.scripts.DocumentationGenerator} and
   * {@link com.google.appinventor.components.scripts.LangDefXmlGenerator},
   * respectively.  The actual implementation appears in {@code runtime.scm}.
   */
  protected void $Initialize() {    // This must be declared protected because we are called from ReplTask which subclasses
    // us and isn't in our package.
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean canDispatchEvent(Component component, String eventName) {
    // Events can only be dispatched after the screen initialized event has completed.
    boolean canDispatch = getTaskInitiliazed() || (component == this && eventName.equals("Initialize"));
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
    return this.getTaskName();
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

  @SimpleFunction(description = "Stop current task")
  public void Stop() {
    Log.i(LOG_TAG, "Stopping task " + this.getTaskName());
    doStop();
  }

  @SimpleFunction(description = "Show task notification")
  public void ShowNotification() {
    getNotification().showNotification();
  }

  @SimpleFunction(description = "Hide task notification")
  public void HideNotification() {
    getNotification().hideNotification();
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
    Log.e(LOG_TAG, "Task " + getTaskName() + " ErrorOccurred, errorNumber = " + errorNumber +
            ", componentType = " + componentType + ", functionName = " + functionName +
            ", messages = " + message);
    if (!EventDispatcher.dispatchEvent(
            this, "ErrorOccurred", component, functionName, errorNumber, message)
            && getTaskInitiliazed()) {
      // If dispatchEvent returned false, then no user-supplied error handler was run.
      // If in addition, the screen initializer was run, then we assume that the
      // user did not provide an error handler.   In this case, we run a default
      // error handler, namely, showing a notification to the end user of the app.
      // The app writer can override this by providing an error handler.
      new Notifier(this).ShowAlert("Error " + errorNumber + ": " + message);
    }
  }

  public void dispatchErrorOccurredEvent(final Component component, final String functionName,
                                         final int errorNumber, final Object... messageArgs) {
    runOnTaskThread(this, new Runnable() {
      @Override
      public void run() {
        String message = ErrorMessages.formatMessage(errorNumber, messageArgs);
        ErrorOccurred(component, functionName, errorNumber, message);
      }
    });
  }

  public void dispatchErrorOccurredEventDialog(final Component component, final String functionName,
                                               final int errorNumber, final Object... messageArgs) {
    runOnTaskThread(this, new Runnable() {
      @Override
      public void run() {
        String message = ErrorMessages.formatMessage(errorNumber, messageArgs);
        // We cannot show Dialog while in Task. We fall back to Alert gracefully in case some component called us.
        ErrorOccurred(component, functionName, errorNumber, message);
      }
    });
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
  public String getContextName() {
    return getTaskName();
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


  //Helper functions to interface to TaskThread

  public String getTaskName() {
    return taskThread.getTaskName();
  }

  public void setTaskType(int type) {
    taskThread.setTaskType(type);
  }

  public int getTaskType() {
    return taskThread.getTaskType();
  }

  public void setTaskInitiliazed(boolean initiliazed) {
    taskThread.setTaskInitiliazed(initiliazed);
  }

  public boolean getTaskInitiliazed() {
    return taskThread.getTaskInitiliazed();
  }

  public TaskNotification getNotification() {
    return taskThread.getTaskNotification();
  }

  public Set<OnDestroyListener> getOnDestroyListeners() {
    return taskThread.getOnDestroyListeners();
  }

  public Set<OnInitializeListener> getOnInitiliazeListeners() {
    return taskThread.getOnInitializeListeners();
  }

  public Set<OnStopListener> getOnStopListeners() {
    return taskThread.getOnStopListeners();
  }


  // This is used by Repl to throttle error messages which can get out of
  // hand, e.g. if triggered by Accelerometer.
  protected boolean toastAllowed() {
    long now = System.nanoTime();
    if (now > lastToastTime + minimumToastWait) {
      lastToastTime = now;
      return true;
    }
    return false;
  }

  // This is called by runtime.scm
  /**
   * Returns the Task instance corresponding to Task Name
   * @param taskName
   */
  public static Task getTask(String taskName) {
    return taskMap.get(taskName);
  }

  public static void runOnTaskThread(String taskName, Runnable runnable) {
    Task task = taskMap.get(taskName);
    if (task == null) {
      throw new IllegalStateException("Tried to run on non-existent task!");
    }
    if (task instanceof ReplTask) {
      ReplTask.runOnTaskThread(taskName, runnable);
    } else {
      Task.runOnTaskThread(task, runnable);
    }
  }

  public static void runOnTaskThread(Task task, Runnable runnable) {
    if (task instanceof ReplTask) {
      ReplTask.runOnTaskThread(task.getTaskName(), runnable);
    } else {
      task.taskThread.getTaskHandler().post(runnable);
    }
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

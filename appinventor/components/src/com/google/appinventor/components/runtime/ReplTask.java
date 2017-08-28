// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import java.util.HashMap;
import java.util.Set;

import android.content.IntentFilter;
import android.os.Handler;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.appinventor.components.runtime.util.EclairUtil;
import com.google.appinventor.components.runtime.util.OnInitializeListener;
import com.google.appinventor.components.runtime.util.SdkLevel;


public class ReplTask extends Task {

  private static final String LOG_TAG = "ReplTask";

  public static ReplTask replTask;
  private static final HashMap<String, ReplTaskThread> taskThreads = new HashMap<String, ReplTaskThread>();

  private TaskNotification replNotification;


  // We create this class to identify ReplTaskThreads from TaskThread
  protected static class ReplTaskThread extends TaskThread {
    public ReplTaskThread(String taskName, ReplTask task) {
      super(taskName, task);
    }

    public void clear() {
      this.taskInitialized = false;
      this.onInitializeListeners.clear();
      this.onStopListeners.clear();
      this.onDestroyListeners.clear();
      this.getTaskNotification().reset();

    }
  }

  public ReplTask() {
    super();
    replTask = this;
  }

  @Override
  public void onCreate() {
    Log.d(LOG_TAG, "ReplTask got onCreate");
    $define();
    LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
            new IntentFilter(Form.LOCAL_ACTION_SEND_MESSAGE));
    replNotification = new TaskNotification("ReplTask", this);
    replNotification.setContentTitle("MIT AI2 Companion");
    replNotification.setContentText("Live Development");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d("ReplTask", "ReplTask onStartCommand Called");
    if (SdkLevel.getLevel() >= SdkLevel.LEVEL_ECLAIR) {
      EclairUtil.startForegroundTask(this, replNotification.getId(),
              replNotification.getNotification());
      replNotification.setShowing(true);
    } else {
      replNotification.showNotification();
    }
    onStartTask(intent, startId);
    // ReplTask is always essentially a Sticky Screen Task
    return START_REDELIVER_INTENT;
  }

  @Override
  protected void onStartTask(Intent intent, int startId) {
    String taskName = intent.getStringExtra(Form.SERVICE_NAME);
    String startValue = intent.getStringExtra(Form.SERVICE_ARG);
    final Object decodedStartVal = Form.decodeJSONStringForForm(startValue, "get start value");
    if (taskName == null) { // This is ReplTask itself Starting
      return;
    }
    // We need to put the same ReplTask in the TaskMap with different taskNames so that Task.runOnTaskThread
    // can find us and call us.
    if (taskMap.get(taskName) == null) {
      taskMap.put(taskName, this);
    }
    ReplTaskThread taskThread = taskThreads.get(taskName);
    if (taskThread != null) {
      if (!taskThread.getTaskInitiliazed()) {
        if (taskThread.getTaskType() == TASK_TYPE_STICKY) {
          taskThread.getTaskNotification().showNotification();
        }
      }
    }

    Runnable onStart = new Runnable() {
      @Override
      public void run() {
        if (!getTaskInitiliazed()) {
          $Initialize();
        }
        if (getTaskType() == TASK_TYPE_STICKY) {
          getNotification().showNotification();
        }
        TaskStarted(decodedStartVal);
      }
    };
    runOnTaskThread(taskName, onStart);
  }

  public static void runOnTaskThread(String taskName, Runnable runnable) {
    ReplTaskThread taskThread = taskThreads.get(taskName);
    if (taskThread == null) {
      taskThread = new ReplTaskThread(taskName, ReplTask.replTask);
      taskThreads.put(taskName, taskThread);
    }
    Handler taskHandler = taskThread.getTaskHandler();
    taskHandler.post(runnable);
  }



  @Override
  protected void doStop() {
    Log.d(LOG_TAG, "Task " + getTaskName() + " got doStop");
    // Invoke all onStopListeners
    Set<OnStopListener> onStopListeners = getOnStopListeners();
    for (OnStopListener onStopListener : onStopListeners) {
      onStopListener.onStop();
    }
    this.onStop();
  }

  @Override
  public void onStop() {
    getCurrentReplTaskThread().clear();
    taskMap.remove(getTaskName());
  }

  @Override
  public void onDestroy() {
    LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    replNotification.hideNotification();
  }

  @Override
  public String getContextName() {
    return getTaskName();
  }


  // Helper functions to interface to TaskThread
  // These functions must be called on a TaskThread for Repl

  // We allow getTaskName() to be called on any thread.
  @Override
  public String getTaskName() {
    if (Thread.currentThread() instanceof ReplTaskThread) {
      return Thread.currentThread().getName();
    }
    return "ReplTask";

  }

  public void setTaskType(int type) {
    getCurrentReplTaskThread().setTaskType(type);
  }

  public int getTaskType() {
    return getCurrentReplTaskThread().getTaskType();
  }

  public void setTaskInitiliazed(boolean initiliazed) {
    getCurrentReplTaskThread().setTaskInitiliazed(initiliazed);
  }

  public boolean getTaskInitiliazed() {
    return getCurrentReplTaskThread().getTaskInitiliazed();
  }

  public TaskNotification getNotification() {
    return getCurrentReplTaskThread().getTaskNotification();
  }

  public Set<OnDestroyListener> getOnDestroyListeners() {
    return getCurrentReplTaskThread().getOnDestroyListeners();
  }

  public Set<OnInitializeListener> getOnInitiliazeListeners() {
    return getCurrentReplTaskThread().getOnInitializeListeners();
  }

  public Set<OnStopListener> getOnStopListeners() {
    return getCurrentReplTaskThread().getOnStopListeners();
  }

  private ReplTaskThread getCurrentReplTaskThread() {
    // we identify current Task by Thread Name
    ReplTaskThread taskThread = taskThreads.get(Thread.currentThread().getName());
    if (taskThread == null) {
      throw new IllegalThreadStateException("getCurrentReplTaskThread() can be called only in a initialized TaskThread");
    }
    return taskThread;
  }

  public boolean isAssetsLoaded() {
    if (ReplForm.topform != null) {
      return ReplForm.topform.isAssetsLoaded();
    }
    return false;
  }


  public static void clearSingleTask(String taskName) {
    Log.d(LOG_TAG, "ReplTask.clearSingleTask : " + taskName + " is called");
    ReplTaskThread taskThread = taskThreads.get(taskName);
    if (taskThread == null ) {
      return;
    }
    Runnable doStop = new Runnable() {
      @Override
      public void run() {
        replTask.doStop();
      }
    };
    runOnTaskThread(taskName, doStop);
  }

  public static void clearAllTasks() {
    for (String taskName : taskThreads.keySet()) {
      clearSingleTask(taskName);
    }
    // We should have a callback to GC after all those runnables are finished
    // For testing this will do
    System.gc();
  }

}
// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.shared.rpc.project.youngandroid;

import com.google.common.base.Preconditions;
import com.google.appinventor.shared.storage.StorageUtil;
import com.google.appinventor.shared.youngandroid.YoungAndroidSourceAnalyzer;


/**
 * Young Android task source file node in the project tree.
 *
 */
public final class YoungAndroidTaskNode extends YoungAndroidSourceNode {

  /**
   * Default constructor (for serialization only).
   */
  public YoungAndroidTaskNode() {
  }

  /**
   * Creates a new Young Android task source file project node.
   *
   * @param fileId  file id
   */
  public YoungAndroidTaskNode(String fileId) {
    super(StorageUtil.basename(fileId), fileId);
  }

  public static String getTaskFileId(String qualifiedName) {
    return SRC_PREFIX + qualifiedName.replace('.', '/')
        + YoungAndroidSourceAnalyzer.TASK_PROPERTIES_EXTENSION;
  }
}

// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2017 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.youngandroid;

import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.editor.ProjectEditor;
import com.google.appinventor.client.editor.simple.SimpleComponentDatabase;
import com.google.appinventor.client.editor.simple.SimpleEditor;
import com.google.appinventor.client.editor.simple.components.MockContext;
import com.google.appinventor.client.explorer.project.ComponentDatabaseChangeListener;
import com.google.appinventor.client.properties.json.ClientJsonParser;
import com.google.appinventor.client.properties.json.ClientJsonString;
import com.google.appinventor.client.youngandroid.YoungAndroidFormUpgrader;
import com.google.appinventor.shared.properties.json.JSONArray;
import com.google.appinventor.shared.properties.json.JSONObject;
import com.google.appinventor.shared.properties.json.JSONParser;
import com.google.appinventor.shared.properties.json.JSONValue;
import com.google.appinventor.shared.rpc.project.FileNode;
import com.google.appinventor.shared.youngandroid.YoungAndroidSourceAnalyzer;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;

import java.util.ArrayList;
import java.util.List;

import static com.google.appinventor.client.Ode.MESSAGES;

public abstract class YaContextEditor extends SimpleEditor implements ComponentDatabaseChangeListener{

  protected static class FileContentHolder {
    private String content;

    FileContentHolder(String content) {
      this.content = content;
    }

    void setFileContent(String content) {
      this.content = content;
    }

    String getFileContent() {
      return content;
    }
  }

  // JSON parser
  protected static final JSONParser JSON_PARSER = new ClientJsonParser();

  protected final SimpleComponentDatabase COMPONENT_DATABASE;

  protected final List<ComponentDatabaseChangeListener> componentDatabaseChangeListeners = new ArrayList<ComponentDatabaseChangeListener>();
  protected JSONArray authURL;    // List of App Inventor versions we have been edited on.

  protected static final int OLD_PROJECT_YAV = 150; // Projects older then this have no authURL

  protected YaContextEditor(ProjectEditor projectEditor, FileNode fileNode) {
    super(projectEditor, fileNode);
    this.COMPONENT_DATABASE = SimpleComponentDatabase.getInstance(getProjectId());

  }

  /**
   * Returns the mock context of the editor
   */
  public abstract MockContext getContext();

  /*
   * Upgrades the given file content, saves the upgraded content back to the
   * ODE server, and calls the afterUpgradeComplete command after the save
   * operation succeeds.
   *
   * If no upgrade is necessary, the afterSavingFiles command is called
   * immediately.
   *
   * @param fileContentHolder  holds the file content
   * @param afterUpgradeComplete  optional command to be executed after the
   *                              file has upgraded and saved back to the ODE
   *                              server
   */
  protected void upgradeFile(FileContentHolder fileContentHolder) {
    JSONObject propertiesObject = YoungAndroidSourceAnalyzer.parseSourceFile(
        fileContentHolder.getFileContent(), JSON_PARSER);

    // BEGIN PROJECT TAGGING CODE

    // |-------------------------------------------------------------------|
    // | Project Tagging Code:                                             |
    // | Because of the likely proliferation of various versions of App    |
    // | Inventor, we want to mark a project with the history of which     |
    // | versions have seen it. We do that with the "authURL" tag which we |
    // | add to the files. It is a JSON array of versions identified       |
    // | by the hostname portion of the URL of the service editing the     |
    // | project. Older projects will not have this field, so if we detect |
    // | an older project (YAV < OLD_PROJECT_YAV) we create the list and   |
    // | add ourselves. If we read in a project where YAV >=               |
    // | OLD_PROJECT_YAV *and* there is no authURL, we assume that it was  |
    // | created on a version of App Inventor that doesn't support project |
    // | tagging and we add an "*UNKNOWN*" tag to indicate this. So for    |
    // | example if you examine a (newer) project and look in the          |
    // | Screen1.scm file, you should just see an authURL that looks like  |
    // | ["ai2.appinventor.mit.edu"]. This would indicate a project that   |
    // | has only been edited on MIT App Inventor. If instead you see      |
    // | something like ["localhost", "ai2.appinventor.mit.edu"] it        |
    // | implies that at some point in its history this project was edited |
    // | using the local dev server on someone's own computer.             |
    // |-------------------------------------------------------------------|

    authURL = (JSONArray) propertiesObject.get("authURL");
    String ourHost = Window.Location.getHostName();
    JSONValue us = new ClientJsonString(ourHost);
    if (authURL != null) {
      List<JSONValue> values = authURL.asArray().getElements();
      boolean foundUs = false;
      for (JSONValue value : values) {
        if (value.asString().getString().equals(ourHost)) {
          foundUs = true;
          break;
        }
      }
      if (!foundUs) {
        authURL.asArray().getElements().add(us);
      }
    } else {
      // Kludgey way to create an empty JSON array. But we cannot call ClientJsonArray ourselves
      // because it is not a public class. So rather then make it public (and violate an abstraction
      // barrier). We create the array this way. Sigh.
      authURL = JSON_PARSER.parse("[]").asArray();
      // Warning: If YaVersion isn't present, we will get an NPF on
      // the line below. But it should always be there...
      // Note: YaVersion although a numeric value is stored as a Json String so we have
      // to parse it as a string and then convert it to a number in Java.
      int yav = Integer.parseInt(propertiesObject.get("YaVersion").asString().getString());
      // If yav is > OLD_PROJECT_YAV, and we still don't have an
      // authURL property then we likely originated from a non-MIT App
      // Inventor instance so add an *Unknown* tag before our tag
      if (yav > OLD_PROJECT_YAV) {
        authURL.asArray().getElements().add(new ClientJsonString("*UNKNOWN*"));
      }
      authURL.asArray().getElements().add(us);
    }

    // END OF PROJECT TAGGING CODE


  }

  public abstract String encodeContextAsJsonString(boolean forYail);

  protected abstract String preUpgradeJsonString();

  public abstract String getComponentInstanceTypeName(String instanceName);
}

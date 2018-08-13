// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2018 MIT, All rights reserved
// This is unreleased code

package com.google.appinventor.components.runtime.util;
import android.os.Environment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;

/**
 * AssetFetcher: This module is used by the MIT AI2 Companion to fetch
 *               assets directly from the App Inventor Server.  Prior
 *               to the use of this module, the App Inventor client
 *               would fetch assets from the server and then send them
 *               to the MIT AI2 Companion. Instead we now use this
 *               module.  It is passed a list of assets to fetch
 *               (which includes extension components). We are also
 *               handed the authentication cookie for the user. We
 *               then fetch the assets from the server and place them
 *               in the appropriate directory in external
 *               storage. Finally when finished we signal the
 *               Companion that we have all of the needed assets
 *
 *               This code is part of the implementation of webRTC
 *               communication between the Companion and the App
 *               Inventor client.
 */

public class AssetFetcher {

  private String uri;
  private String cookieName;
  private String cookieValue;
  private long projectId;

  private static final String LOG_TAG = AssetFetcher.class.getSimpleName();

  private static final String REPL_ASSET_DIR =
    Environment.getExternalStorageDirectory().getAbsolutePath() +
    "/AppInventor/";

  // We use a single threaded executor so we only load one asset at a time!
  private static ExecutorService background = Executors.newSingleThreadExecutor();

  /* We are only used statically */
  private AssetFetcher() {
  }

  public static void fetchAssets(final String cookieValue,
    final String projectId, final String uri, final String asset) {
    background.submit(new Runnable() {
        @Override
        public void run() {
          try {
            String aUri = uri + "/ode/download/file/" + projectId + "/" + asset;
            URL url = new URL(aUri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.addRequestProperty("Cookie",  "AppInventor = " + cookieValue);
            if (connection != null) {
              int responseCode = connection.getResponseCode();
              Log.d(LOG_TAG, "asset = " + asset + " responseCode = " + responseCode);
              File outFile = new File(REPL_ASSET_DIR + asset);
              File parentOutFile = outFile.getParentFile();
              if (!parentOutFile.exists()) {
                parentOutFile.mkdirs();
              }
              BufferedInputStream in = new BufferedInputStream(connection.getInputStream(), 0x1000);
              BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile), 0x1000);
              try {
                while (true) {
                  int b = in.read();
                  if (b == -1) {
                    break;
                  }
                  out.write(b);
                }
                out.flush();
              } catch (IOException e) {
                Log.e(LOG_TAG, "copying assets", e);
              } finally {
                out.close();
              }
            }
            connection.disconnect();
            RetValManager.assetTransferred(asset);
          } catch (Exception e) {
            Log.e(LOG_TAG, "Exception", e); // Cop out
          }
        }
      });
  }

  // For testing
  // AssetFetcher INSTANCE = null;

  // public static void TestFetcher(long projectId, String cookie, String asset) {
  //   Log.d(LOG_TAG, "TestFetcher: projectId = " + projectId + " asset = " + asset + " cookie = " + cookie);
  //   AssetFetcher tester = new AssetFetcher("http://jis.qyv.net:8888", "AppInventor", cookie, projectId);
  //   List<String> assetList = (List<String>) new ArrayList();
  //   assetList.add(asset);
  //   tester.fetchAssets(assetList);
  // }

}

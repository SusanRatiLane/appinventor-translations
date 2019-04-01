// -*- mode: java; c-basic-offset: 2; -*-
// Copyright © 2019 MIT, All rights reserved.
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package edu.mit.appinventor.pkginstall;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.NougatUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@DesignerComponent(version = 1,
  category = ComponentCategory.EXTENSION,
  description = "An extension to replace the package installer functionality.",
  nonVisible = true)
@UsesPermissions(Manifest.permission.REQUEST_INSTALL_PACKAGES)
@SimpleObject(external = true)
public class PackageInstaller extends AndroidNonvisibleComponent {
  private static final String LOG_TAG = PackageInstaller.class.getSimpleName();
  private static final String REPL_ASSET_DIR =
      Environment.getExternalStorageDirectory().getAbsolutePath() +
          "/AppInventor/assets/";

  public PackageInstaller(Form form) {
    super(form);
  }

  @SimpleFunction(description = "Install the specified APK file")
  public void InstallAPK(final String urlToApk) {
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run() {
        Uri packageuri = null;
        try {
          URL url = new URL(urlToApk);
          URLConnection conn = url.openConnection();
          File rootDir = new File(REPL_ASSET_DIR);
          InputStream instream = new BufferedInputStream(conn.getInputStream());
          File apkfile = new File(rootDir + "/package.apk");
          FileOutputStream apkOut = new FileOutputStream(apkfile);
          byte[] buffer = new byte[32768];
          int len;
          while ((len = instream.read(buffer, 0, 32768)) > 0) {
            apkOut.write(buffer, 0, len);
          }
          instream.close();
          apkOut.close();
          // Call Package Manager Here
          Log.d(LOG_TAG, "About to Install package from " + urlToApk);
          Intent intent = new Intent(Intent.ACTION_VIEW);
          packageuri = NougatUtil.getPackageUri(form, apkfile);
          intent.setDataAndType(packageuri, "application/vnd.android.package-archive");
          intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          form.startActivity(intent);
        } catch (ActivityNotFoundException e) {
          Log.e(LOG_TAG, "Unable to install package", e);
          form.dispatchErrorOccurredEvent(form, "PackageInstaller",
              ErrorMessages.ERROR_UNABLE_TO_INSTALL_PACKAGE, packageuri);
        } catch (Exception e) {
          Log.e(LOG_TAG, "ERROR_UNABLE_TO_GET", e);
          form.dispatchErrorOccurredEvent(form, "PackageInstaller",
              ErrorMessages.ERROR_WEB_UNABLE_TO_GET, urlToApk);
        }
      }
    });
  }
}

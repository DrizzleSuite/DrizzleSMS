package org.grovecity.drizzlesms.util;

import android.content.Context;

import java.io.IOException;

public class VersionTracker {


  public static int getLastSeenVersion(Context context) {
    return DrizzleSmsPreferences.getLastVersionCode(context);
  }

  public static void updateLastSeenVersion(Context context) {
    try {
      int currentVersionCode = Util.getCurrentApkReleaseVersion(context);
      DrizzleSmsPreferences.setLastVersionCode(context, currentVersionCode);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
}

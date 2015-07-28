package org.grovecity.drizzlesms.util;

import android.app.Activity;
import android.content.Intent;

import org.grovecity.drizzlesms.R;

public class DynamicTheme {

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  protected int getSelectedTheme(Activity activity) {
    String theme = DrizzleSmsPreferences.getTheme(activity);

    if (theme.equals("dark")) return R.style.Drizzle_DarkTheme;

    return R.style.Drizzle_LightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}

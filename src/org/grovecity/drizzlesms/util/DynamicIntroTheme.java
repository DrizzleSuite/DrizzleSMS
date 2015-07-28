package org.grovecity.drizzlesms.util;

import android.app.Activity;

import org.grovecity.drizzlesms.R;

public class DynamicIntroTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    String theme = DrizzleSmsPreferences.getTheme(activity);

    if (theme.equals("dark")) return R.style.Drizzle_DarkIntroTheme;

    return R.style.Drizzle_LightIntroTheme;
  }
}

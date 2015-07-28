package org.grovecity.drizzlesms.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;

import org.grovecity.drizzlesms.ApplicationPreferencesActivity;
import org.grovecity.drizzlesms.R;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;

import java.util.Arrays;

public class AppearancePreferenceFragment extends ListSummaryPreferenceFragment {

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_appearance);

    this.findPreference(DrizzleSmsPreferences.THEME_PREF).setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(DrizzleSmsPreferences.LANGUAGE_PREF).setOnPreferenceChangeListener(new ListSummaryListener());
    initializeListSummary((ListPreference)findPreference(DrizzleSmsPreferences.THEME_PREF));
    initializeListSummary((ListPreference)findPreference(DrizzleSmsPreferences.LANGUAGE_PREF));
  }

  @Override
  public void onStart() {
    super.onStart();
    getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener((ApplicationPreferencesActivity)getActivity());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__appearance);
  }

  @Override
  public void onStop() {
    super.onStop();
    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener((ApplicationPreferencesActivity) getActivity());
  }

  public static CharSequence getSummary(Context context) {
    String[] languageEntries     = context.getResources().getStringArray(R.array.language_entries);
    String[] languageEntryValues = context.getResources().getStringArray(R.array.language_values);
    String[] themeEntries        = context.getResources().getStringArray(R.array.pref_theme_entries);
    String[] themeEntryValues    = context.getResources().getStringArray(R.array.pref_theme_values);

    int langIndex  = Arrays.asList(languageEntryValues).indexOf(DrizzleSmsPreferences.getLanguage(context));
    int themeIndex = Arrays.asList(themeEntryValues).indexOf(DrizzleSmsPreferences.getTheme(context));

    return context.getString(R.string.preferences__theme_summary,    themeEntries[themeIndex]) + ", " +
           context.getString(R.string.preferences__language_summary, languageEntries[langIndex]);
  }
}

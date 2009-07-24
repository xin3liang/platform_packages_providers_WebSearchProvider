/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.websearch;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.util.Log;

/**
 * Activity for setting web search engine preferences.
 */
public class Settings extends PreferenceActivity implements OnPreferenceClickListener {

    private static final boolean DBG = false;
    private static final String TAG = "WebSearchProvider";

    private static final String SHOW_WEB_SUGGESTIONS_PREF = "show_web_suggestions";

    private SearchEngineInfo mEngine;
    private String mEngineLabel;

    private CheckBoxPreference mShowWebSuggestionsPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (DBG) Log.d(TAG, "onCreate(), intent=" + intent);
        ComponentName component = WebSearch.componentNameFromUri(intent.getData());
        mEngine = WebSearch.getSearchEngine(this, component);
        if (mEngine == null) {
            Log.e(TAG, "Unknown search engine " + intent.getData());
            finish();
            return;
        }
        mEngineLabel = getActivityLabel(component);
        if (mEngineLabel == null) {
            Log.w(TAG, "No label for search engine " + intent.getData());
            finish();
            return;
        }

        setTitle(getString(R.string.search_engine_settings, mEngineLabel));

        addPreferencesFromResource(R.xml.preferences);

        mShowWebSuggestionsPreference = (CheckBoxPreference) findPreference(
                SHOW_WEB_SUGGESTIONS_PREF);
        mShowWebSuggestionsPreference.setOnPreferenceClickListener(this);

        updateShowWebSuggestionsPreference();
    }

    private String getActivityLabel(ComponentName component) {
        if (component == null) return null;
        try {
            PackageManager pm = getPackageManager();
            ActivityInfo activityInfo = pm.getActivityInfo(component, 0);
            int labelRes = activityInfo.labelRes;
            if (labelRes == 0) labelRes = activityInfo.applicationInfo.labelRes;
            if (labelRes == 0) return null;
            return getString(labelRes);
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(TAG, "Error loading web search source from activity "
                    + component, exception);
            return null;
        }
    }

    public synchronized boolean onPreferenceClick(Preference preference) {
        if (preference == mShowWebSuggestionsPreference) {
            System.putInt(
                    getContentResolver(),
                    System.SHOW_WEB_SUGGESTIONS,
                    mShowWebSuggestionsPreference.isChecked() ? 1 : 0);
            return true;
        }
        return false;
    }

    /**
     * Updates the "show web suggestions" preference from the value in system settings.
     */
    private void updateShowWebSuggestionsPreference() {
        boolean enabled = mEngine.supportsSuggestions();
        mShowWebSuggestionsPreference.setEnabled(enabled);
        if (!enabled) {
            String summary =
                getString(R.string.show_web_suggestions_not_supported, mEngineLabel);
            mShowWebSuggestionsPreference.setSummary(summary);
            return;
        }
        String summaryOn =
            getString(R.string.show_web_suggestions_summary_enabled, mEngineLabel);
        String summaryOff =
            getString(R.string.show_web_suggestions_summary_disabled, mEngineLabel);
        mShowWebSuggestionsPreference.setSummaryOn(summaryOn);
        mShowWebSuggestionsPreference.setSummaryOff(summaryOff);
        int value;
        try {
            value = System.getInt(
                    getContentResolver(), System.SHOW_WEB_SUGGESTIONS);
        } catch (SettingNotFoundException e) {
            // No setting found, create one.
            System.putInt(getContentResolver(), System.SHOW_WEB_SUGGESTIONS, 1);
            value = 1;
        }
        mShowWebSuggestionsPreference.setChecked(value == 1);
    }
}

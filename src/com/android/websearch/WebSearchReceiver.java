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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.util.LogPrinter;

import com.android.internal.app.ResolverActivity;

import java.util.List;

/**
 * This class receives the broadcast event of boot completed from the system and sets up the default
 * web search provider activity if required.
 */
public class WebSearchReceiver extends BroadcastReceiver {
    private static final String TAG = "WebSearch";

    /**
     * Checks if the given activity component is present in the system and if so makes it the
     * preferred activity for handling ACTION_WEB_SEARCH.
     * @param componentName Name of the component to check and set as preferred.
     * @return true if component was detected and set as preferred activity, false if not.
     */
    private boolean setPreferredActivity(Context context, String componentName) {
        Log.d(TAG, "Checking component " + componentName);
        ComponentName activity = ComponentName.unflattenFromString(componentName);
        PackageManager pm = context.getPackageManager();
        ActivityInfo ai;
        try {
            ai = pm.getActivityInfo(activity, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        // The code here to find the value for bestMatch is heavily inspired by the code
        // in ResolverActivity where the preferred activity is set.
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> webSearchActivities = pm.queryIntentActivities(intent, 0);
        ComponentName set[] = new ComponentName[webSearchActivities.size()];
        int bestMatch = 0;
        for (int i = 0; i < webSearchActivities.size(); ++i) {
            ResolveInfo ri = webSearchActivities.get(i);
            set[i] = new ComponentName(ri.activityInfo.packageName,
                                       ri.activityInfo.name);
            if (ri.match > bestMatch) bestMatch = ri.match;
        }

        Log.d(TAG, "Setting preferred web search activity to " + componentName);
        IntentFilter filter = new IntentFilter(Intent.ACTION_WEB_SEARCH);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        pm.replacePreferredActivity(filter, bestMatch, set, activity);
        return true;
    }

    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        Log.d(TAG, "boot completed.");

        // Check if we have a preferred web search activity.
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        PackageManager pm = context.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (ri == null || ri.activityInfo.name.equals(ResolverActivity.class.getName())) {
            Log.d(TAG, "No preferred activity set for action web search.");

            // The components in the providers array are checked in the order of declaration so the
            // first one has the highest priority. If the component exists in the system it is set
            // as the preferred activity to handle intent action web search.
            String[] preferredActivities = context.getResources().getStringArray(
                    R.array.default_providers);
            for (String componentName : preferredActivities) {
                if (setPreferredActivity(context, componentName)) {
                    break;
                }
            }
        }
    }
}

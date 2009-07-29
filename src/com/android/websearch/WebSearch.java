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

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.util.Log;

/**
 * This class routes the web search intent action on web suggestion results to the browser and opens
 * up the appropriate search engine results page.
 */
public class WebSearch extends Activity {
    public static final String LOG_TAG = "WebSearch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_WEB_SEARCH.equals(action) || Intent.ACTION_SEARCH.equals(action)) {
            SearchEngineInfo engine = getSearchEngine(this, intent.getComponent());
            if (engine != null) {                
                // The browser can pass along an application id which it uses to figure out which
                // window to place a new search into. So if this exists, we'll pass it back to
                // the browser. Otherwise, add our own package name as the application id, so that
                // the browser can organize all searches launched from this provider together.
                String applicationId = intent.getStringExtra(Browser.EXTRA_APPLICATION_ID);
                if (applicationId == null) {
                    applicationId = getPackageName();
                }
                
                // Format the URI to launch and open it in the browser.
                String query = intent.getStringExtra(SearchManager.QUERY);
                String launchUri = engine.getSearchUriForQuery(query);
                if (launchUri == null) {
                    Log.e(LOG_TAG, "Unable to get search URI for engine "
                            + intent.getComponent());
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(launchUri));
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, applicationId);
                    startActivity(intent);
                }
            }
        } else if (SearchManager.INTENT_ACTION_WEB_SEARCH_SETTINGS.equals(action)) {
            Intent settingsIntent = new Intent(SearchManager.INTENT_ACTION_WEB_SEARCH_SETTINGS);
            settingsIntent.setClass(this, Settings.class);
            ComponentName component = intent.getComponent();
            settingsIntent.setData(componentNameToUri(component));
            startActivity(settingsIntent);
        }
        finish();
    }

    public static SearchEngineInfo getSearchEngine(Context context, ComponentName component) {
        if (component == null) return null;
        String[] parts = component.getClassName().split("\\.");
        String engine_index = parts[parts.length - 1];
        try {
            return new SearchEngineInfo(context, engine_index);
        } catch (IllegalArgumentException exception) {
            Log.e(LOG_TAG, "Cannot load search engine index " + engine_index, exception);
            return null;
        }
    }

    // TODO: Maybe this should live in ComponentName?
    public static Uri componentNameToUri(ComponentName component) {
        if (component == null) return null;
        return new Uri.Builder()
                .scheme("package")
                .authority(component.getPackageName())
                .path(component.getClassName())
                .query("")
                .fragment("")
                .build();
    }

    // TODO: Maybe this should live in ComponentName?
    public static ComponentName componentNameFromUri(Uri activityUri) {
        if (activityUri == null) return null;
        String packageName = activityUri.getAuthority();
        String className = activityUri.getLastPathSegment();
        if (packageName == null || className == null) return null;
        return new ComponentName(packageName, className);
    }


}

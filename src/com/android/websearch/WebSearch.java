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
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
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
        if (intent != null && Intent.ACTION_WEB_SEARCH.equals(intent.getAction())) {
            // Fetch the provider index for which this action was launched and load the provider
            // data.
            String[] parts = intent.getComponent().getClassName().split("\\.");
            String engine_index = parts[parts.length - 1];
            SearchEngineInfo engine = null;
            try {
                engine = new SearchEngineInfo((Context)this, engine_index);

                // Format the URI to launch and open it in the browser.
                String query = intent.getStringExtra(SearchManager.QUERY);
                String launchUri = engine.getSearchUriForQuery(query);
                if (launchUri == null) {
                    Log.e(LOG_TAG, "Unable to get search URI for engine index " + engine_index);
                } else {
                    intent = new Intent(Intent.ACTION_SEARCH);
                    intent.putExtra(SearchManager.QUERY, launchUri);
                    intent.setComponent(new ComponentName(
                            "com.android.browser", "com.android.browser.BrowserActivity"));
                    startActivity(intent);
                }
            } catch (IllegalArgumentException exception) {
                Log.e(LOG_TAG, "Cannot load search engine index " + engine_index, exception);
            }
        }
        finish();
    }
}

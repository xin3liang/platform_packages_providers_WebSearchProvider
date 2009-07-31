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

import com.android.internal.database.ArrayListCursor;
import com.google.android.net.GoogleHttpClient;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Provides search suggestions, if any, for a given web search provider.
 */
public class SuggestionProvider extends ContentProvider {
    private static final String USER_AGENT = "Android/1.0";
    private static final int HTTP_TIMEOUT_MS = 1000;

    // TODO: this should be defined somewhere
    private static final String HTTP_TIMEOUT = "http.connection-manager.timeout";

    private static final String LOG_TAG = "WebSearch.SuggestionProvider";

    // Indices of the columns in the below arrays.
    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_QUERY = 1;
    private static final int COLUMN_INDEX_ICON = 2;
    private static final int COLUMN_INDEX_TEXT_1 = 3;
    private static final int COLUMN_INDEX_TEXT_2 = 4;

    // The suggestion columns used. If you are adding a new entry to these arrays make sure to
    // update the list of indices declared above.
    private static final String[] COLUMNS = new String[] {
        "_id",
        SearchManager.SUGGEST_COLUMN_QUERY,
        SearchManager.SUGGEST_COLUMN_ICON_1,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
        SearchManager.SUGGEST_COLUMN_TEXT_2,
    };

    private static final String[] COLUMNS_WITHOUT_DESCRIPTION = new String[] {
        "_id",
        SearchManager.SUGGEST_COLUMN_QUERY,
        SearchManager.SUGGEST_COLUMN_ICON_1,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
    };

    private HttpClient mHttpClient;

    private final HashMap<String, SearchEngineInfo> mSearchEngines =
        new HashMap<String, SearchEngineInfo>();

    @Override
    public boolean onCreate() {
        mHttpClient = new GoogleHttpClient(getContext().getContentResolver(),
                USER_AGENT, false /* not gzip capable */);
        HttpParams params = mHttpClient.getParams();
        params.setLongParameter(HTTP_TIMEOUT, HTTP_TIMEOUT_MS);

        return true;
    }

    /**
     * This will always return {@link SearchManager#SUGGEST_MIME_TYPE} as this
     * provider is purely to provide suggestions.
     */
    @Override
    public String getType(Uri uri) {
        return SearchManager.SUGGEST_MIME_TYPE;
    }

    /**
     * Queries for a given search term and returns a cursor containing
     * suggestions ordered by best match.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String query = selectionArgs[0];
        if (TextUtils.isEmpty(query)) {
            return null;
        }
        if (!isNetworkConnected()) {
            Log.i(LOG_TAG, "Not connected to network.");
            return null;
        }

        List<String> parts = uri.getPathSegments();
        if (parts.size() < 2) {
            Log.i(LOG_TAG, "Invalid URI " + uri.toString());
            return null;
        }

        // Check if the specific search engine data is already loaded and if not load it now.
        String engine_index = parts.get(parts.size() - 2);
        SearchEngineInfo engine = null;
        synchronized(this) {
            engine = mSearchEngines.get(engine_index);
            if (engine == null) {
                try {
                    engine = new SearchEngineInfo(getContext(), engine_index);
                } catch (IllegalArgumentException exception) {
                    Log.e(LOG_TAG, "Cannot load search engine index " + engine_index, exception);
                    return null;
                }
                mSearchEngines.put(engine_index, engine);
            }
        }

        String suggestUri = engine.getSuggestUriForQuery(query);
        if (TextUtils.isEmpty(suggestUri)) {
            // No suggest URI available for this engine
            return null;
        }

        try {
            HttpPost method = new HttpPost(suggestUri);
            StringEntity content = new StringEntity("");
            method.setEntity(content);
            HttpResponse response = mHttpClient.execute(method);
            if (response.getStatusLine().getStatusCode() == 200) {
                /* The data format is a JSON array with items being regular strings or JSON arrays
                 * themselves. We are interested in the second and third elements, both of which
                 * should be JSON arrays. The second element/array contains the suggestions and the
                 * third element contains the descriptions. Some search engines don't support
                 * suggestion descriptions so the third element is optional.
                 */
                JSONArray results = new JSONArray(EntityUtils.toString(response.getEntity()));
                JSONArray suggestions = results.getJSONArray(1);
                JSONArray descriptions = null;
                if (results.length() > 2) {
                    descriptions = results.getJSONArray(2);
                    // Some search engines given an empty array "[]" for descriptions instead of
                    // not including it in the response.
                    if (descriptions.length() == 0) {
                        descriptions = null;
                    }
                }
                return new SuggestionsCursor(suggestions, descriptions);
            }
        } catch (UnsupportedEncodingException e) {
            Log.w(LOG_TAG, "Error", e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Error", e);
        } catch (JSONException e) {
            Log.w(LOG_TAG, "Error", e);
        }
        return null;
    }

    private boolean isNetworkConnected() {
        NetworkInfo networkInfo = getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivity =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return null;
        }
        return connectivity.getActiveNetworkInfo();
    }

    private static class SuggestionsCursor extends AbstractCursor {

        /* Contains the actual suggestions */
        final JSONArray mSuggestions;

        /* This contains the popularity of each suggestion
         * i.e. 165,000 results. It's not related to sorting.
         */
        final JSONArray mDescriptions;
        public SuggestionsCursor(JSONArray suggestions, JSONArray descriptions) {
            mSuggestions = suggestions;
            mDescriptions = descriptions;
        }

        @Override
        public int getCount() {
            return mSuggestions.length();
        }

        @Override
        public String[] getColumnNames() {
            return (mDescriptions != null ? COLUMNS : COLUMNS_WITHOUT_DESCRIPTION);
        }

        @Override
        public String getString(int column) {
            if ((mPos != -1)) {
                if ((column == COLUMN_INDEX_QUERY) || (column == COLUMN_INDEX_TEXT_1)) {
                    try {
                        return mSuggestions.getString(mPos);
                    } catch (JSONException e) {
                        Log.w(LOG_TAG, "Error", e);
                    }
                } else if (column == COLUMN_INDEX_TEXT_2) {
                    try {
                        return mDescriptions.getString(mPos);
                    } catch (JSONException e) {
                        Log.w(LOG_TAG, "Error", e);
                    }
                } else if (column == COLUMN_INDEX_ICON) {
                    return String.valueOf(R.drawable.magnifying_glass);
                }
            }
            return null;
        }

        @Override
        public double getDouble(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(int column) {
            if (column == COLUMN_INDEX_ID) {
                return mPos;        // use row# as the _Id
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNull(int column) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}

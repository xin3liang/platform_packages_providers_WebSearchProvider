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

import java.net.URLEncoder;
import java.util.Locale;

import android.content.Context;
import android.content.ComponentName;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;


/**
 * Loads and holds data for a given web search engine.
 */
public class SearchEngineInfo {
    private static String LOG_TAG = "SearchEngineInfo";

    // The fields of a search engine data array, defined in the same order as they appear in the
    // all_search_engines.xml file.
    // If you are adding/removing to this list, remember to update NUM_FIELDS below.
    static final int FIELD_KEYWORD = 0;
    static final int FIELD_FAVICON_URI = 1;
    static final int FIELD_SEARCH_URI = 2;
    static final int FIELD_ENCODING = 3;
    static final int FIELD_SUGGEST_URI = 4;
    static final int NUM_FIELDS = 5;

    // The OpenSearch URI template parameters that we support.
    private static final String PARAMETER_LANGUAGE = "{language}";
    private static final String PARAMETER_SEARCH_TERMS = "{searchTerms}";
    private static final String PARAMETER_INPUT_ENCODING = "{inputEncoding}";

    // The array of strings defining this search engine. The array values are in the same order as
    // the above enumeration definition.
    private String[] mSearchEngineData = null;

    private Context mContext = null;

    /*
     * @param index is the numerical value in the search engine name resource, starts from 1. This
     * is typically obtained as the last word in the content URI path field or the intent class name
     * field.
     * @throws IllegalArgumentException If the index does not refer to a valid search engine
     */
    public SearchEngineInfo(Context context, String index) throws IllegalArgumentException {
        mContext = context;
        String resourceName = "engine_" + index + "_name";
        Resources res = mContext.getResources();
        int id_name = res.getIdentifier(resourceName, "string", mContext.getPackageName());
        if (id_name == 0) {
            throw new IllegalArgumentException("No such resource - " + resourceName);
        }

        String searchEngineName = res.getString(id_name);
        int id_data = res.getIdentifier(searchEngineName, "array", mContext.getPackageName());
        mSearchEngineData = res.getStringArray(id_data);

        if (mSearchEngineData == null) {
            throw new IllegalArgumentException("No data found for " + resourceName);
        }
        if (mSearchEngineData.length != NUM_FIELDS) {
            throw new IllegalArgumentException(
                    resourceName + " has invalid number of fields - " + mSearchEngineData.length);
        }
        if (TextUtils.isEmpty(mSearchEngineData[FIELD_SEARCH_URI])) {
            throw new IllegalArgumentException(resourceName + " has an empty search URI");
        }

        // Add the current language/country information to the URIs.
        Locale locale = mContext.getResources().getConfiguration().locale;
        StringBuilder language = new StringBuilder(locale.getLanguage());
        if (!TextUtils.isEmpty(locale.getCountry())) {
            language.append('-');
            language.append(locale.getCountry());
        }

        String language_str = language.toString();
        mSearchEngineData[FIELD_SEARCH_URI] =
            mSearchEngineData[FIELD_SEARCH_URI].replace(PARAMETER_LANGUAGE, language_str);
        mSearchEngineData[FIELD_SUGGEST_URI] =
            mSearchEngineData[FIELD_SUGGEST_URI].replace(PARAMETER_LANGUAGE, language_str);

        // Default to UTF-8 if not specified.
        String enc = mSearchEngineData[FIELD_ENCODING];
        if (TextUtils.isEmpty(enc)) {
            enc = "UTF-8";
            mSearchEngineData[FIELD_ENCODING] = enc;
        }

        // Add the input encoding method to the URI.
        mSearchEngineData[FIELD_SEARCH_URI] =
            mSearchEngineData[FIELD_SEARCH_URI].replace(PARAMETER_INPUT_ENCODING, enc);
        mSearchEngineData[FIELD_SUGGEST_URI] =
            mSearchEngineData[FIELD_SUGGEST_URI].replace(PARAMETER_INPUT_ENCODING, enc);
    }

    /*
     * Returns the URI for launching a web search with the given query (or null if there was no
     * data available for this search engine).
     */
    public String getSearchUriForQuery(String query) {
        return getFormattedUri(searchUri(), query);
    }

    /*
     * Returns the URI for retrieving web search suggestions for the given query (or null if there
     * was no data available for this search engine).
     */
    public String getSuggestUriForQuery(String query) {
        return getFormattedUri(suggestUri(), query);
    }

    public String faviconUri() {
        return mSearchEngineData[FIELD_FAVICON_URI];
    }

    private String suggestUri() {
        return mSearchEngineData[FIELD_SUGGEST_URI];
    }

    private String searchUri() {
        return mSearchEngineData[FIELD_SEARCH_URI];
    }

    /*
     * Formats a launchable uri out of the template uri by replacing the template parameters with
     * actual values.
     */
    private String getFormattedUri(String templateUri, String query) {
        if (TextUtils.isEmpty(templateUri)) {
            return null;
        }

        // Encode the query terms in the requested encoding (and fallback to UTF-8 if not).
        String enc = mSearchEngineData[FIELD_ENCODING];
        try {
            return templateUri.replace(PARAMETER_SEARCH_TERMS, URLEncoder.encode(query, enc));
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "Exception occured when encoding query " + query + " to " + enc);
            return null;
        }
    }
}

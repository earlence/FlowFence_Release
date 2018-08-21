/*
 * Copyright (C) 2017 The Regents of the University of Michigan
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

package edu.umich.flowfence.policy;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.umich.flowfence.service.EventChannel;
import edu.umich.flowfence.helpers.Utils;


public class PackageManifest {
    private static final String TAG = "FF.Policy";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private static final String MANIFEST_META_DATA = "edu.umich.flowfence.manifest";

    private final HashMap<String, Source> sourceMap;
    private final HashMap<String, EventChannel> channelMap;

    public PackageManifest(Context packageContext) throws XmlPullParserException, IOException {
        String packageName = packageContext.getPackageName();
        if (localLOGD) {
            Log.d(TAG, "Loading manifest for " + packageName);
        }

        PackageManager pm = packageContext.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = packageContext.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // This shouldn't happen - we already have the package info!
            Log.wtf(TAG, "Package snuck away when we weren't looking", e);
            throw new PolicyParseException(e);
        }

        sourceMap = new HashMap<>();
        channelMap = new HashMap<>();

        Resources resources = packageContext.getResources();
        XmlResourceParser _parser = ai.loadXmlMetaData(pm, MANIFEST_META_DATA);
        if (_parser == null) {
            PolicyParseException e = new PolicyParseException("Missing manifest for package "+packageName);
            Log.e(TAG, Log.getStackTraceString(e));
            throw e;
        }

        // Yes, this looks stupid; yes, it's necessary because of the way try-with-resources works.
        // If we moved the loadXmlMetaData call here, we'd get a NullPointerException when
        // the compiler's generated code tried to close it.
        try (XmlResourceParser parser = _parser) {
            while (parser.next() != XmlPullParser.START_TAG);
            parser.require(XmlPullParser.START_TAG, "", "FlowfenceManifest");

            while (parser.nextTag() != XmlPullParser.END_TAG) {
                switch (parser.getName()) {
                    case "source":
                        Source src = new Source(packageName, parser, resources);
                        sourceMap.put(src.getSourceName().getClassName(), src);
                        break;
                    case "event-channel":
                        EventChannel ec = new EventChannel(packageName, parser, resources);
                        channelMap.put(ec.getChannelName().getClassName(), ec);
                        break;
                    default:
                        Log.w(TAG, "Unknown manifest element " + parser.getName());
                        Utils.skip(parser);
                }
            }

            parser.require(XmlPullParser.END_TAG, "", "FlowfenceManifest");
        }
    }

    public Map<String, Source> getSources() {
        return Collections.unmodifiableMap(sourceMap);
    }

    public Map<String, EventChannel> getChannels() {
        return Collections.unmodifiableMap(channelMap);
    }
}

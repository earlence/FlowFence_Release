/*
 * Copyright (C) 2017 The Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this fil e except in compliance with the License.
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

import android.content.ComponentName;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.service.FlowfenceApplication;
import edu.umich.flowfence.service.Sandbox;

public final class Policy {
    private static final String TAG = "OASIS.Policy";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);
    private static final String TAG_NAME = "policy";

    private final Source source;
    private final List<Rule> rules;

    public Policy(Source source) {
        this.source = source;
        this.rules = Collections.emptyList();
    }

    public Policy(Source source, XmlResourceParser parser, Resources resources) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, "", TAG_NAME);
        this.source = source;
        this.rules = new ArrayList<>();

        while (parser.nextTag() != XmlPullParser.END_TAG) {
            Rule r = Rule.parseRule(this, parser, resources);
            if (r != null) {
                rules.add(r);
            }
        }
        parser.require(XmlPullParser.END_TAG, "", TAG_NAME);
    }

    public static boolean checkCallerSink(SinkRequest request) {
        return checkSink(Sandbox.getCallingSandbox().getTaints(), request);
    }

    public static boolean checkSink(TaintSet ts, SinkRequest request) {
        Objects.requireNonNull(request);
        if (ts == null) {
            // no taint; everything succeeds
            return true;
        }

        Map<ComponentName, Float> amounts = ts.asMap();

        if (localLOGD) {
            Log.d(TAG, "Evaluating "+request+" for "+ts);
        }

        for (ComponentName taintName : amounts.keySet()) {
            PackageManifest manifest = FlowfenceApplication.getInstance().getManifestForPackage(taintName.getPackageName());
            if (manifest == null) {
                Log.e(TAG, "Couldn't find manifest for "+taintName.getPackageName());
                request.reject();
                continue;
            }

            Source source = manifest.getSources().get(taintName.getClassName());
            if (source == null) {
                Log.e(TAG, "Couldn't find source "+taintName.flattenToShortString());
                request.reject();
                continue;
            }

            if (localLOGD) {
                Log.d(TAG, "Evaluating rules for " + taintName.flattenToShortString());
            }

            source.getPolicy().evaluateRules(request);
        }

        Map<Source, String> errorMessages = request.getErrorMessages();
        if (!errorMessages.isEmpty()) {
            SecurityException se = null;
            for (String message : errorMessages.values()) {
                if (se == null) {
                    se = new SecurityException(message);
                } else {
                    se.addSuppressed(new SecurityException(message));
                }
            }
            throw se;
        }

        return !request.isRejected();
    }

    public Source getSource() {
        return source;
    }

    public void evaluateRules(SinkRequest request) {
        for (Rule r : rules) {
            if (!r.process(request)) {
                return;
            }
        }

        // TODO: policy is default allow; add sensible defaults
    }
}

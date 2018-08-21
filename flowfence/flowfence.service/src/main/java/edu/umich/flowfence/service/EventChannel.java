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

package edu.umich.flowfence.service;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.umich.flowfence.common.CallFlags;
import edu.umich.flowfence.common.CallParam;
import edu.umich.flowfence.common.ParceledPayload;
import edu.umich.flowfence.common.QMDescriptor;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.events.IEventChannelReceiver;
import edu.umich.flowfence.events.IEventChannelSender;
import edu.umich.flowfence.helpers.Utils;
import edu.umich.flowfence.policy.PolicyParseException;

public final class EventChannel {
    private static final String TAG = "FF.EventChannel";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private static final String EVENT_CHANNEL_PREFIX = "event:";
    private static final String NS_TAINT_SET = "taint-set";
    private static final String NS_DESCRIPTOR_TAINT = "taint";
    private static final String NS_SUBSCRIBERS = "subscribers";
    private static final String KEY_SUBSCRIBERS = "all";

    private static final String[] exportedOptions = {
            "none",
            "subscribe",
            "fire",
            "both"
    };

    private final FlowfenceApplication mApplication;
    private final ComponentName mChannelName;
    private final boolean mExportsSubscribe;
    private final boolean mExportsFire;
    private final NamespaceSharedPrefs mPrefs;
    private Map<QMRef, TaintSet> mInvocationList = new HashMap<>();
    // TODO: transient receivers

    public EventChannel(String packageName, XmlResourceParser parser, Resources res)
            throws XmlPullParserException, IOException {
        mApplication = FlowfenceApplication.getInstance();

        parser.require(XmlPullParser.START_TAG, "", "event-channel");

        String channelName = parser.getAttributeValue(Utils.FLOWFENCE_NAMESPACE, "name");
        if (channelName == null) {
            throw new PolicyParseException("Missing flowfence:name attribute on channel");
        }
        mChannelName = new ComponentName(packageName, channelName);

        int exports = parser.getAttributeListValue(Utils.FLOWFENCE_NAMESPACE, "exported", exportedOptions, -1);
        if (exports == -1) {
            Log.w(TAG, "Can't understand flowfence:exported attribute, assuming no export");
            exports = 0;
        }

        Utils.skip(parser);
        parser.require(XmlPullParser.END_TAG, "", "event-channel");

        mExportsSubscribe = (exports & 0x1) != 0;
        mExportsFire = (exports & 0x2) != 0;

        String prefsName = EVENT_CHANNEL_PREFIX + mChannelName.flattenToShortString().replace('/', ':');
        SharedPreferences prefs = mApplication.getSharedPreferences(prefsName, 0);
        mPrefs = NamespaceSharedPrefs.get(prefs, NS_TAINT_SET, NS_DESCRIPTOR_TAINT);
    }

    private synchronized Map<QMRef, TaintSet> getInvocationList() throws Exception {
        if (mInvocationList == null) {
            Set<String> stringDescs = mPrefs.getStringSet(NS_SUBSCRIBERS, KEY_SUBSCRIBERS,
                    Collections.<String>emptySet());

            Map<QMRef, TaintSet> invList = new HashMap<>(stringDescs.size());

            for (String descStr : stringDescs) {
                QMDescriptor desc = QMDescriptor.parse(descStr);
                QMRef ref = mApplication.resolveQM(desc, 0);
                TaintSet ts = mPrefs.getTaint(NS_DESCRIPTOR_TAINT, descStr, TaintSet.EMPTY);
                invList.put(ref, ts);
            }

            mInvocationList = invList;
        }

        return mInvocationList;
    }

    public synchronized void subscribe(QMDescriptor desc, QMRef ref, TaintSet ts) throws Exception {
        String packageName = desc.definingClass.getPackageName();
        if (!mExportsSubscribe && !packageName.equals(mChannelName.getPackageName())) {
            throw new SecurityException("Package "+packageName+" can't subscribe to channel "+mChannelName);
        }

        String descString = desc.toString();
        Set<String> currentDescriptors = mPrefs.getStringSet(NS_SUBSCRIBERS, KEY_SUBSCRIBERS,
                Collections.<String>emptySet());

        if (!currentDescriptors.contains(descString)) {
            Log.i(TAG, "Subscribing "+descString+" to channel "+mChannelName);

            if (ref == null) {
                ref = mApplication.resolveQM(desc, 0);
            }

            getInvocationList().put(ref, ts);

            currentDescriptors = new HashSet<>(currentDescriptors);
            currentDescriptors.add(descString);
            NamespaceSharedPrefs.Editor editor = mPrefs.edit();
            editor.putStringSet(NS_SUBSCRIBERS, KEY_SUBSCRIBERS, currentDescriptors);

            if (!TaintSet.EMPTY.equals(ts)) {
                editor.putTaint(NS_DESCRIPTOR_TAINT, descString, ts);
            } else {
                editor.remove(NS_DESCRIPTOR_TAINT, descString);
            }

            editor.apply();
        }
    }

    public synchronized void unsubscribe(QMDescriptor desc, QMRef ref, TaintSet ts) throws Exception {
        String descString = desc.toString();
        Set<String> currentCopy = new HashSet<>(mPrefs.getStringSet(NS_SUBSCRIBERS,
                KEY_SUBSCRIBERS, Collections.<String>emptySet()));

        if (currentCopy.remove(descString)) {
            // Only remove from the invocation list if we've already loaded it.
            // If we haven't, just remove the descriptor from the SharedPreferences,
            // and the changes will apply when we do.
            if (mInvocationList != null) {
                if (ref == null) {
                    // The resolution will always be cached if it's on the invocation list
                    // and we've previously initialized the list.
                    ref = mApplication.resolveQM(desc, 0);
                }
                mInvocationList.remove(ref);
            }

            NamespaceSharedPrefs.Editor editor = mPrefs.edit();
            editor.putStringSet(NS_SUBSCRIBERS, KEY_SUBSCRIBERS, currentCopy);

            if (!TaintSet.EMPTY.equals(ts)) {
                editor.putTaint(NS_DESCRIPTOR_TAINT, descString, ts);
            } else {
                editor.remove(NS_DESCRIPTOR_TAINT, descString);
            }

            editor.apply();
        }
    }

    public ComponentName getChannelName() {
        return mChannelName;
    }

    private final IEventChannelSender sender = new IEventChannelSender.Stub() {
        @Override
        public ComponentName getChannelName() {
            return EventChannel.this.getChannelName();
        }

        @Override
        public void fire(List<ParceledPayload> parceledArgs, TaintSet extraTaint) throws RemoteException {
            Sandbox caller = Sandbox.getCallingSandbox();
            String packageName = caller.getAssignedPackage();
            Objects.requireNonNull(packageName);
            if (!mExportsFire && !packageName.equals(mChannelName.getPackageName())) {
                throw new SecurityException("Package "+packageName+" can't fire channel "+mChannelName.flattenToShortString());
            }
            if (parceledArgs == null) {
                parceledArgs = Collections.emptyList();
            }
            List<CallParam> callParamList = new ArrayList<>(parceledArgs.size());

            TaintSet ts = caller.getTaints();

            if (extraTaint != null && !TaintSet.EMPTY.equals(extraTaint)) {
                ts = ts.asBuilder().unionWith(extraTaint).build();
            }

            for (ParceledPayload payload : parceledArgs) {
                CallParam cp = new CallParam();
                cp.setData(payload, 0);
                callParamList.add(cp);
            }

            callParamList = Collections.unmodifiableList(callParamList);
            Map<QMRef, TaintSet> invocationList;
            try {
                invocationList = getInvocationList();
            } catch (Exception e) {
                Log.e(TAG, "Error getting invocation list for channel "+mChannelName.flattenToShortString(), e);
                return;
            }

            List<CallRecord> records = new ArrayList<>(invocationList.size());
            for (Map.Entry<QMRef, TaintSet> receiver : invocationList.entrySet()) {
                QMRef ref = receiver.getKey();
                try {
                    TaintSet callTaint = ts;
                    TaintSet subscriptionTaint = receiver.getValue();

                    if (!TaintSet.EMPTY.equals(subscriptionTaint)) {
                        callTaint = callTaint.asBuilder().unionWith(subscriptionTaint).build();
                    }

                    final int flags = CallFlags.CALL_ASYNC | CallFlags.NO_RETURN_VALUE;
                    CallRecord record = new CallRecord(ref, flags, callParamList, callTaint);
                    records.add(record);

                    // Deallocate the output handles now, we don't need them (since there's nowhere to pass them to).
                    SparseArray<Handle> outputs = record.getOutHandles();
                    for (int i = 0; i < outputs.size(); i++) {
                        outputs.valueAt(i).release();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error firing "+ref.getDescriptor()+" for channel "+mChannelName.flattenToShortString(), t);
                }
            }

            for (CallRecord record : records) {
                record.waitForReady();
            }
        }

        @Override
        public void registerTransientReceiver(IEventChannelReceiver receiver) {
            // TODO
        }

        @Override
        public void deregisterTransientReceiver(IEventChannelReceiver receiver) throws RemoteException {
            // TODO
        }
    };

    public IEventChannelSender getSender() {
        return sender;
    }
}

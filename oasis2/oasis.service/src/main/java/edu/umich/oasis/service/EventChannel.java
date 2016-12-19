package edu.umich.oasis.service;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.IBinder;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.umich.oasis.common.CallFlags;
import edu.umich.oasis.common.CallParam;
import edu.umich.oasis.common.CallResult;
import edu.umich.oasis.common.ExceptionResult;
import edu.umich.oasis.common.ParceledPayload;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.events.IEventChannelReceiver;
import edu.umich.oasis.events.IEventChannelSender;
import edu.umich.oasis.helpers.Utils;
import edu.umich.oasis.policy.PolicyParseException;

/**
 * Created by jpaupore on 2/7/16.
 */
public final class EventChannel {
    private static final String TAG = "OASIS.EventChannel";
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

    private final OASISApplication mApplication;
    private final ComponentName mChannelName;
    private final boolean mExportsSubscribe;
    private final boolean mExportsFire;
    private final NamespaceSharedPrefs mPrefs;
    private Map<SodaRef, TaintSet> mInvocationList = new HashMap<>();
    // TODO: transient receivers

    public EventChannel(String packageName, XmlResourceParser parser, Resources res)
            throws XmlPullParserException, IOException {
        mApplication = OASISApplication.getInstance();

        parser.require(XmlPullParser.START_TAG, "", "event-channel");

        String channelName = parser.getAttributeValue(Utils.OASIS_NAMESPACE, "name");
        if (channelName == null) {
            throw new PolicyParseException("Missing oasis:name attribute on channel");
        }
        mChannelName = new ComponentName(packageName, channelName);

        int exports = parser.getAttributeListValue(Utils.OASIS_NAMESPACE, "exported", exportedOptions, -1);
        if (exports == -1) {
            Log.w(TAG, "Can't understand oasis:exported attribute, assuming no export");
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

    private synchronized Map<SodaRef, TaintSet> getInvocationList() throws Exception {
        if (mInvocationList == null) {
            Set<String> stringDescs = mPrefs.getStringSet(NS_SUBSCRIBERS, KEY_SUBSCRIBERS,
                    Collections.<String>emptySet());

            Map<SodaRef, TaintSet> invList = new HashMap<>(stringDescs.size());

            for (String descStr : stringDescs) {
                SodaDescriptor desc = SodaDescriptor.parse(descStr);
                SodaRef ref = mApplication.resolveSODA(desc, 0);
                TaintSet ts = mPrefs.getTaint(NS_DESCRIPTOR_TAINT, descStr, TaintSet.EMPTY);
                invList.put(ref, ts);
            }

            mInvocationList = invList;
        }

        return mInvocationList;
    }

    public synchronized void subscribe(SodaDescriptor desc, SodaRef ref, TaintSet ts) throws Exception {
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
                ref = mApplication.resolveSODA(desc, 0);
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

    public synchronized void unsubscribe(SodaDescriptor desc, SodaRef ref, TaintSet ts) throws Exception {
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
                    ref = mApplication.resolveSODA(desc, 0);
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
            Map<SodaRef, TaintSet> invocationList;
            try {
                invocationList = getInvocationList();
            } catch (Exception e) {
                Log.e(TAG, "Error getting invocation list for channel "+mChannelName.flattenToShortString(), e);
                return;
            }

            List<CallRecord> records = new ArrayList<>(invocationList.size());
            for (Map.Entry<SodaRef, TaintSet> receiver : invocationList.entrySet()) {
                SodaRef ref = receiver.getKey();
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

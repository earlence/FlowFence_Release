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

package edu.umich.oasis.service;

import android.os.ConditionVariable;
import android.os.Debug;
import android.util.Log;

import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONException;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import edu.umich.oasis.common.OASISConstants;
import edu.umich.oasis.common.TaintSet;

public class SandboxManager {
    private static final String TAG = "OASIS.SandboxManager";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);
    private static final int SANDBOX_COUNT = OASISConstants.NUM_SANDBOXES;

    private static final float COST_START_PROCESS = 50.0f;
    private static final float COST_LOAD_CODE = 10.0f;
    private static final float COST_RESOLVE_SODA = 3.0f;
    private static final float COST_MARSHAL_OUT = 1.0f;
    private static final float COST_MARSHAL_IN = 1.0f;

    private final LinkedHashMap<Sandbox, ObjectUtils.Null> mIdleSandboxes = new LinkedHashMap<>(SANDBOX_COUNT*2, 0.75f, true);
    private final ArrayDeque<Sandbox> mStoppedSandboxes = new ArrayDeque<>(SANDBOX_COUNT);
    private final HashSet<Sandbox> mRunningSandboxes = new HashSet<>(SANDBOX_COUNT*2);
    private final ArrayDeque<Sandbox> mHotSpares = new ArrayDeque<>(SANDBOX_COUNT);

    private final LinkedList<AsyncCallback> mPendingCallbacks = new LinkedList<>();

    private final Object mExecutionReferenceKey = new Object();

    private int mMaxCount = 0;
    private int mMaxIdleCount = SANDBOX_COUNT;
    private int mMinHotSpares = 1;
    private int mMaxHotSpares = SANDBOX_COUNT;

    public SandboxManager() {
    }

    public interface AsyncCallback {
        Sandbox tryFindSandbox(SandboxManager manager);
        void execute(Sandbox finalChoice);
    }

    private static abstract class FutureCallback implements AsyncCallback {
        private volatile boolean mComplete = false;
        private Sandbox mSandbox = null;

        @Override
        public void execute(Sandbox sb) {
            synchronized (this) {
                boolean wasComplete = mComplete;
                mComplete = true;
                if (!wasComplete) {
                    mSandbox = sb;
                    this.notifyAll();
                }
            }
        }

        public Sandbox await() {
            synchronized (this) {
                while (!mComplete) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                    }
                }
                return mSandbox;
            }
        }
    }

    private static void dumpSandbox(Sandbox sb, BitSet seen) {
        int id = sb.getID();
        String result = String.format("    %s %s package=%s", sb, sb.getTaints(), sb.getAssignedPackage());
        if (seen == null) {
            Log.w(TAG, result+" [LEAKED]");
        } else if (seen.get(id)) {
            Log.w(TAG, result+" [DUPLICATE]");
        } else {
            Log.v(TAG, result);
            seen.set(id);
        }
    }

    private void dumpSandboxes() {
        if (localLOGV) {
            BitSet seenSandboxes = new BitSet(SANDBOX_COUNT);
            Log.v(TAG, ">>> Dumping current sandbox state:");
            Log.v(TAG, "Running: "+mRunningSandboxes.size()+" sandboxes");
            for (Sandbox sb : mRunningSandboxes) {
                dumpSandbox(sb, seenSandboxes);
            }
            Log.v(TAG, "Idle: "+mIdleSandboxes.size()+" sandboxes (LRU order)");
            for (Sandbox sb : mIdleSandboxes.keySet()) {
                dumpSandbox(sb, seenSandboxes);
            }
            Log.v(TAG, "Stopped: "+mStoppedSandboxes.size()+" sandboxes");
            for (Sandbox sb : mStoppedSandboxes) {
                dumpSandbox(sb, seenSandboxes);
            }
            Log.v(TAG, "Hot spares: "+mHotSpares.size()+" sandboxes");
            for (Sandbox sb : mHotSpares) {
                dumpSandbox(sb, seenSandboxes);
            }
            seenSandboxes.flip(0, SANDBOX_COUNT); // true = unseen
            if (!seenSandboxes.isEmpty()) {
                Log.w(TAG, "WARNING: leaked "+seenSandboxes.cardinality()+" sandboxes");
                int leaked = -1;
                while ((leaked = seenSandboxes.nextSetBit(leaked+1)) >= 0) {
                    dumpSandbox(Sandbox.get(leaked), null);
                }
            } else {
                Log.v(TAG, "No leaks detected");
            }
            Log.v(TAG, "<<< End of state dump");
        }
    }

    private synchronized Map<AsyncCallback, Sandbox> tryExecuteQueueLocked() {
        Map<AsyncCallback, Sandbox> rv = null;

        Iterator<AsyncCallback> callbackIter = mPendingCallbacks.iterator();
        while (callbackIter.hasNext()) {
            AsyncCallback callback = callbackIter.next();

            Sandbox candidate = tryBeginExecution(callback.tryFindSandbox(this));

            if (candidate != null) {
                if (rv == null) {
                    rv = new LinkedHashMap<>();
                }
                callbackIter.remove();
                rv.put(callback, candidate);
            }
        }

        return rv;
    }

    private void tryExecuteQueueUnlocked(Map<AsyncCallback, Sandbox> toExecute) {
        if (toExecute != null) {
            for (Map.Entry<AsyncCallback, Sandbox> entry : toExecute.entrySet()) {
                entry.getKey().execute(entry.getValue());
            }
        }
    }

    private void tryExecuteQueue() {
        Map<AsyncCallback, Sandbox> map;
        synchronized (this) {
            map = tryExecuteQueueLocked();
        }
        tryExecuteQueueUnlocked(map);
    }

    public int setMaxSandboxCount(int count) {
        Map<AsyncCallback, Sandbox> callbacks = null;
        boolean shouldRunQueue = false;
        int oldCount;

        synchronized (this) {
            oldCount = mMaxCount;
            int newCount = Math.max(0, Math.min(count, SANDBOX_COUNT));
            Log.i(TAG, String.format("Changing sandbox count from %d to %d", oldCount, newCount));
            dumpSandboxes();

            if (newCount < oldCount) {
                // Taking sandboxes away.
                for (int i = oldCount - 1; i >= newCount; i--) {
                    Sandbox sb = Sandbox.get(i);
                    if (mStoppedSandboxes.remove(sb)) {
                        // Was previously stopped; do nothing.
                    } else if (mIdleSandboxes.remove(sb) != null) {
                        // Was previously idle; stop.
                        sb.stop(this);
                    } else if (mHotSpares.remove(sb)) {
                        // Was previously hot spare; stop and replace hot spare.
                        sb.stop(this);
                    } else if (mRunningSandboxes.remove(sb)) {
                        // Was previously running; let it keep running, putSandbox() will ignore
                        // this sandbox when the time comes.
                    }
                }
            } else if (newCount > oldCount) {
                // Adding sandboxes.
                for (int i = oldCount; i < newCount; i++) {
                    Sandbox sb = Sandbox.get(i);
                    if (!mRunningSandboxes.contains(sb)) {
                        mStoppedSandboxes.add(sb);
                    }
                }
                // Wake up people waiting for a new hot spare.
                shouldRunQueue = true;
            }

            mMaxCount = newCount;

            refillHotSpares();

            if (shouldRunQueue) {
                callbacks = tryExecuteQueueLocked();
            }

            dumpSandboxes();
        }

        if (shouldRunQueue) {
            tryExecuteQueueUnlocked(callbacks);
        }

        return oldCount;
    }

    public synchronized int setMaxIdleSandboxCount(int count) {
        int oldCount = mMaxIdleCount;
        int newCount = Math.max(0, Math.min(count, SANDBOX_COUNT));
        Log.i(TAG, "Changing max idle count from "+oldCount+" to "+newCount);
        dumpSandboxes();

        mMaxIdleCount = newCount;
        trimIdle();
        dumpSandboxes();
        return oldCount;
    }

    public synchronized int setMinHotSpare(int count) {
        int oldCount = mMinHotSpares;
        int newCount = Math.max(0, Math.min(count, mMaxHotSpares));
        Log.i(TAG, "Changing min hot spares from " + oldCount + " to " + newCount);
        dumpSandboxes();

        mMinHotSpares = newCount;
        if (mHotSpares.size() < mMinHotSpares) {
            refillHotSpares();
        }

        return oldCount;
    }

    public synchronized int setMaxHotSpare(int count) {
        int oldCount = mMaxHotSpares;
        int newCount = Math.max(mMinHotSpares, Math.min(count, SANDBOX_COUNT));
        Log.i(TAG, "Changing max hot spares from " + oldCount + " to " + newCount);
        dumpSandboxes();

        mMaxHotSpares = newCount;
        while (mHotSpares.size() > mMaxHotSpares) {
            mIdleSandboxes.put(mHotSpares.remove(), ObjectUtils.NULL);
        }
        trimIdle();

        return oldCount;
    }

    public synchronized void start() {
        setMaxSandboxCount(SANDBOX_COUNT);
    }

    public synchronized void stop() {
        setMaxSandboxCount(0);
    }

    private Sandbox evictIdle() {
        Map.Entry<Sandbox, ?> eldest = mIdleSandboxes.eldest();
        Sandbox victim = eldest.getKey();
        mIdleSandboxes.remove(victim);
        return victim;
    }

    private void trimIdle() {
        while (mIdleSandboxes.size() > mMaxIdleCount) {
            Sandbox victim = evictIdle();
            if (mHotSpares.size() < mMaxHotSpares) {
                victim.restart();
                mHotSpares.add(victim);
            } else {
                victim.stop(this);
                mStoppedSandboxes.add(victim);
            }
        }
    }

    private void refillHotSpares() {
        while (mHotSpares.size() < mMinHotSpares && addHotSpare()) {
            dumpSandboxes();
        }
    }

    private boolean addHotSpare() {
        Sandbox newHotSpare = null;
        // Now replace the hot spare.
        if (!mStoppedSandboxes.isEmpty()) {
            newHotSpare = mStoppedSandboxes.poll();
            if (localLOGD) {
                Log.d(TAG, "Use stopped sandbox "+newHotSpare);
            }
            newHotSpare.start(this);
        } else if (!mIdleSandboxes.isEmpty()) {
            newHotSpare = evictIdle();
            if (localLOGD) {
                Log.d(TAG, "Use idle sandbox "+newHotSpare);
            }
            newHotSpare.restart();
        } else {
            Log.w(TAG, "No more hot spare candidates available");
        }

        if (newHotSpare != null) {
            mHotSpares.add(newHotSpare);
            return true;
        } else {
            return false;
        }
    }


    private synchronized Sandbox tryGetHotSpare() {
        Sandbox result;
        while ((result = mHotSpares.peek()) == null) {
            if (addHotSpare()) {
                continue;
            }

            return null;
        }
        if (localLOGD) {
            Log.d(TAG, "Using hot spare " + result);
        }
        refillHotSpares();
        return result;
    }

    private synchronized Sandbox tryBeginExecution(Sandbox sb) {
        if (sb == null) {
            return null;
        }

        if (mHotSpares.peek() == sb) {
            mHotSpares.remove();
        } else if (mIdleSandboxes.remove(sb) == null) {
            Log.e(TAG, "Sandbox not idle when we're trying to execute in it");
        }

        mRunningSandboxes.add(sb);
        sb.start(mExecutionReferenceKey);

        return sb;
    }

    private synchronized Sandbox tryGetSandboxForResolve(String packageName) {
        Sandbox cheapestSandbox = null;
        if (localLOGD) {
            Log.d(TAG, "getSandboxForResolve " + packageName);
            dumpSandboxes();
        }

        // Start out with the hot spare.
        float cheapestCost = !mHotSpares.isEmpty() ? COST_LOAD_CODE + 0.01f : Float.POSITIVE_INFINITY;

        // Is there an idle sandbox that's cheaper than the hot spare?
        for (Sandbox sb : mIdleSandboxes.keySet()) {
            String assignedPackage = sb.getAssignedPackage();
            if (assignedPackage != null && !assignedPackage.equals(packageName)) {
                continue;
            }

            float cost = 0.0f;
            if (!sb.hasLoadedPackage(packageName)) {
                // Need to load this package into this sandbox.
                cost += COST_LOAD_CODE;
            }

            if (localLOGD) {
                Log.d(TAG, String.format("%s: cost %g", sb, cost));
            }

            // We don't care about taints or anything like that.
            if (cost < cheapestCost) {
                cheapestCost = cost;
                cheapestSandbox = sb;
            }
        }

        if (localLOGD) {
            Log.d(TAG, String.format("Final choice: %s, cost %g",
                                     (cheapestSandbox == null) ? "hot spare" : cheapestSandbox,
                                     cheapestCost));
        }
        return (cheapestSandbox != null) ? cheapestSandbox : tryGetHotSpare();
    }

    public synchronized Sandbox tryGetSandboxForCall(CallRecord record) {
        Sandbox cheapestSandbox = null;

        if (localLOGD) {
            Log.d(TAG, "getSandboxForCall " + record);
            dumpSandboxes();
        }
        int numHandles = record.getPredecessors().size();
        int numUnmarshalledHandles = 0;
        for (Handle h : record.getPredecessors()) {
            if (!h.isMarshalled()) {
                numUnmarshalledHandles++;
            }
        }

        // Start out with the hot spare.
        float cheapestCost = !mHotSpares.isEmpty() ?
                COST_LOAD_CODE + COST_RESOLVE_SODA + 0.01f +
                        COST_MARSHAL_OUT * numUnmarshalledHandles + COST_MARSHAL_IN * numHandles :
                Float.POSITIVE_INFINITY;
        if (localLOGD) {
            Log.d(TAG, String.format("%d handles, %d unmarshalled", numHandles, numUnmarshalledHandles));
            Log.d(TAG, String.format("Hot spare cost: %g", cheapestCost));
        }

        // Can we get away with an idle sandbox?
        TaintSet inboundTaint = record.getInboundTaints();
        if (localLOGD) {
            Log.d(TAG, String.format("Inbound taint: %s", inboundTaint));
        }
        final String packageName = record.getSODA().getDescriptor().definingClass.getPackageName();
        for (Sandbox sb : mIdleSandboxes.keySet()) {
            String assignedPackage = sb.getAssignedPackage();
            if (assignedPackage != null && !assignedPackage.equals(packageName)) {
                continue;
            }

            TaintSet sandboxTaint = sb.getTaints();
            boolean mustMarshalOut = false;
            if (!sandboxTaint.isSubsetOf(inboundTaint)) {
                // SB more tainted than inbound; skip this SB
                continue;
            } else if (!inboundTaint.isSubsetOf(sandboxTaint)) {
                // Inbound more tainted than SB; must marshal things out of SB
                mustMarshalOut = true;
            }

            float cost = 0.0f;
            if (!sb.hasLoadedPackage(packageName)) {
                // Need to load this package into this sandbox.
                cost += COST_LOAD_CODE;
            }

            if (!record.getSODA().isResolvedIn(sb)) {
                // Need to resolve the SODA in this sandbox.
                cost += COST_RESOLVE_SODA;
            }

            if (mustMarshalOut) {
                cost += COST_MARSHAL_OUT * sb.countUnmarshalledObjects();
            }

            for (Handle h : record.getPredecessors()) {
                if (!h.isLiveIn(sb)) {
                    cost += COST_MARSHAL_IN;
                    if (!h.isMarshalled()) {
                        cost += COST_MARSHAL_OUT;
                    }
                } else if (h.getRefCount() == 1) {
                    // We won't need to marshal this one out after all.
                    cost -= COST_MARSHAL_OUT;
                }
            }

            if (localLOGD) {
                Log.d(TAG, String.format("%s %s: cost %g", sb, sandboxTaint, cost));
            }

            if (cost < cheapestCost) {
                cheapestCost = cost;
                cheapestSandbox = sb;
            }
        }

        if (localLOGD) {
            Log.d(TAG, String.format("Final choice: %s, cost %g",
                    (cheapestSandbox == null) ? "hot spare" : cheapestSandbox,
                    cheapestCost));
        }

        return (cheapestSandbox != null) ? cheapestSandbox : tryGetHotSpare();
    }

    public synchronized Sandbox tryGetSandboxById(final int id, final CallRecord record) {
        final Sandbox sb = Sandbox.get(id);
        if (localLOGD) {
            Log.d(TAG, "getSandboxById " + sb);
        }
        if (mRunningSandboxes.contains(sb)) {
            Log.i(TAG, "Explicitly requested sandbox " + sb + " busy");
            return null;
        }

        if (mStoppedSandboxes.remove(sb)) {
            sb.start(this);
        }
        if (mHotSpares.remove(sb)) {
            refillHotSpares();
        }
        // Put into mIdleSandboxes temporarily, beginExecution expects it.
        mIdleSandboxes.put(sb, ObjectUtils.NULL);

        if (record != null) {
            String packageName = record.getSODA().getDescriptor().definingClass.getPackageName();
            String assignedName = sb.getAssignedPackage();
            if ((assignedName != null && !assignedName.equals(packageName)) ||
                    !record.getInboundTaints().isSubsetOf(sb.getTaints())) {
                // We need to restart this sandbox for security reasons.
                Log.i(TAG, "Restarting " + sb + " for explicit request");
                sb.restart();
            }
        }

        return sb;
    }

    public void getSandboxAsync(AsyncCallback callback) {
        Map<AsyncCallback, Sandbox> callbacks;
        synchronized (this) {
            Objects.requireNonNull(callback);
            mPendingCallbacks.add(callback);
            callbacks = tryExecuteQueueLocked();
        }
        tryExecuteQueueUnlocked(callbacks);
    }

    public Sandbox getSandboxForResolve(final String packageName) {
        FutureCallback cb = new FutureCallback() {
            @Override
            public Sandbox tryFindSandbox(SandboxManager manager) {
                return manager.tryGetSandboxForResolve(packageName);
            }
        };
        getSandboxAsync(cb);
        return cb.await();
    }

    public Sandbox getSandboxById(final int id, final CallRecord record) {
        FutureCallback cb = new FutureCallback() {
            @Override
            public Sandbox tryFindSandbox(SandboxManager manager) {
                return manager.tryGetSandboxById(id, record);
            }
        };
        getSandboxAsync(cb);
        return cb.await();
    }

    public void putSandbox(Sandbox sb) {
        Map<AsyncCallback, Sandbox> callbacks;
        synchronized (this) {
            if (!mRunningSandboxes.remove(sb)) {
                Log.w(TAG, "Put non-running sandbox " + sb);
                return;
            }

            sb.stop(mExecutionReferenceKey);

            if (localLOGD) {
                Log.d(TAG, "putSandbox " + sb);
            }

            // This was a trimmed sandbox. Let it go out of circulation.
            if (sb.getID() >= mMaxCount) {
                sb.stop(this);
                return;
            }

            mIdleSandboxes.put(sb, ObjectUtils.NULL);

            refillHotSpares();
            trimIdle();

            callbacks = tryExecuteQueueLocked();
        }

        tryExecuteQueueUnlocked(callbacks);
    }

    public synchronized void onPackageRemoved(String packageName) {
        for (Sandbox sb : mIdleSandboxes.keySet()) {
            if (sb.hasLoadedPackage(packageName)) {
                sb.restart();
            }
        }
    }
}

package edu.umich.oasis.service;

import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.umich.oasis.common.CallFlags;
import edu.umich.oasis.common.CallParam;
import edu.umich.oasis.common.CallResult;
import edu.umich.oasis.common.HandleDestroyedException;
import edu.umich.oasis.common.OASISConstants;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.internal.IResolvedSoda;
import edu.umich.oasis.internal.ISandboxObject;
import edu.umich.oasis.internal.ISodaCallback;

/**
 * Created by jpaupore on 2/2/15.
 */
/*package*/ final class CallRecord extends ISodaCallback.Stub implements SandboxManager.AsyncCallback {
    private static final String TAG = "OASIS.CallRecord";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private static final AtomicInteger g_mNextRecordId = new AtomicInteger();

    private static final int STATE_WAITING = 0x1; // waiting for predecessors
    private static final int STATE_READY   = 0x2; // predecessors ready, awaiting execution
    private static final int STATE_RUNNING = 0x3; // currently executing
    private static final int STATE_SUCCESS = 0x4; // execution completed successfully
    private static final int STATE_FAILURE = 0x5; // execution resulted in an exception

    private static final String[] STATE_DESCRIPTIONS = {
            "unknown",
            "waiting",
            "ready",
            "running",
            "succeeded",
            "failed"
    };

    // Lock: protects everything.
    // CV: unblocked when all data ready.
    private final ConditionVariable mSync = new ConditionVariable();
    private final SodaRef mSoda;
    private final List<CallParam> mCallParams;
    private final SparseArray<Handle> mOutParams;
    private final HashSet<Handle> mPendingPredecessors;
    private final HashSet<Handle> mAllPredecessors;
    private final TaintSet mExtraTaint;
    private final int mFlags;
    private final int mRecordId;
    private Sandbox mRunningSandbox;
    private int mState;
    private TaintSet mInboundTaints;

    public CallRecord(SodaRef soda, int flags, List<CallParam> callParams, TaintSet extraTaint) {
        mSoda = soda;
        mPendingPredecessors = new HashSet<>();
        mAllPredecessors = new HashSet<>();
        mCallParams = new ArrayList<>(callParams.size());
        mOutParams = new SparseArray<>();
        mFlags = flags;
        mRecordId = g_mNextRecordId.getAndIncrement();
        mState = STATE_WAITING;
        mExtraTaint = (extraTaint != null) ? extraTaint : TaintSet.EMPTY;

        // Synchronize here, since we may get callbacks for data ready.
        synchronized (mSync) {
            // Build handles and set up links.
            if ((flags & CallFlags.NO_RETURN_VALUE) == 0) {
                Handle returnValue = new Handle(this, CallResult.RETURN_VALUE);
                mOutParams.append(CallResult.RETURN_VALUE, returnValue);
            }

            for (int i = 0; i < callParams.size(); i++) {
                CallParam param = callParams.get(i);

                // If this param was from a previous SODA, take a dependency on it.
                // Otherwise, just use the param as-is.
                int paramFlags = param.getHeader();
                switch (param.getType()) {
                    case CallParam.TYPE_NULL:
                    case CallParam.TYPE_DATA:
                        mCallParams.add(param);
                        break;

                    case CallParam.TYPE_HANDLE:
                        Handle pred = (Handle) param.getPayload();
                        boolean releaseAtomic = ((paramFlags & CallParam.HANDLE_RELEASE) != 0);
                        // Link this call in as a successor to that handle.
                        if (pred.addSuccessor(this, releaseAtomic)) {
                            mPendingPredecessors.add(pred);
                        }
                        mAllPredecessors.add(pred);
                        mCallParams.add(param);
                }

                // If this param is marshaled out, set up a return handle for it.
                if ((paramFlags & CallParam.FLAG_RETURN) != 0) {
                    mOutParams.append(i, new Handle(this, i));
                }
            }

            if (mPendingPredecessors.isEmpty()) {
                scheduleForExecutionLocked();
            }
        }
    }

    private void scheduleForExecutionLocked() {
        mState = STATE_READY;
        OASISApplication.getInstance().getService().addRef();
        OASISApplication.getInstance().getSandboxAsync(this);
    }

    /*package*/ int getFlags() {
        return mFlags;
    }

    /*package*/ SodaRef getSODA() {
        return mSoda;
    }

    /*package*/ void onDataReady(Handle handle) {
        synchronized (mSync) {
            mPendingPredecessors.remove(handle);
            if (mPendingPredecessors.isEmpty()) {
                scheduleForExecutionLocked();
            }
        }
    }

    /*package*/ Set<Handle> getPredecessors() {
        return Collections.unmodifiableSet(mAllPredecessors);
    }

    /*package*/ TaintSet getInboundTaints() {
        synchronized (mSync) {
            if (mState != STATE_READY) {
                throw new IllegalStateException("Not ready to execute yet");
            }

            if (mInboundTaints != null) {
                return mInboundTaints;
            }

            TaintSet.Builder tsb = mExtraTaint.asBuilder();

            tsb.unionWith(mSoda.getRequiredTaints());

            for (Handle h : getPredecessors()) {
                tsb.unionWith(h.getTaint());
            }

            TaintSet ts = tsb.build();

            if (localLOGV) {
                Log.v(TAG, "Inbound taints for " + this + ": " + ts);
            }

            mInboundTaints = ts;
            return ts;
        }
    }

    @Override
    public Sandbox tryFindSandbox(SandboxManager manager) {
        if ((mFlags & CallFlags.OVERRIDE_SANDBOX) != 0) {
            int sandboxId = (mFlags & CallFlags.SANDBOX_NUM_MASK) % OASISConstants.NUM_SANDBOXES;
            return manager.tryGetSandboxById(sandboxId, this);
        } else {
            return manager.tryGetSandboxForCall(this);
        }
    }

    @Override
    public void execute(Sandbox sandbox) {
        if (localLOGV) {
            Log.v(TAG, "Executing CallRecord "+this);
        }
        sandbox.waitForStartupComplete();
        TaintSet inboundTaints = getInboundTaints();
        if (!sandbox.getTaints().isSubsetOf(inboundTaints)) {
            Log.w(TAG, "Sandbox "+sandbox+" has unexpected taints");
        }
        sandbox.beginExecute(this);
        mRunningSandbox = sandbox;
        synchronized (mSync) {
            // Resolve first.
            Throwable throwable = null;
            IResolvedSoda resolvedSoda = null;
            try {
                if (localLOGV) {
                    Log.v(TAG, "Resolving "+getSODA().getDescriptor());
                }
                resolvedSoda = mSoda.resolveFor(sandbox);
                if (localLOGV) {
                    Log.v(TAG, "Resolved "+getSODA().getDescriptor());
                }
            } catch (Throwable t) {
                throwable = t;
            }

            if (localLOGD) {
                Log.d(TAG, String.format("Preparing call for %s", mSoda.getDescriptor()));
            }

            // Prepare arguments.
            List<CallParam> outboundParams = new ArrayList<>(mCallParams.size());
            for (int i = 0; i < mCallParams.size(); i++) {
                try {
                    CallParam outbound = Handle.getOutboundCallParam(sandbox, mCallParams.get(i), this);
                    if (localLOGD) {
                        Log.d(TAG, Objects.toString(outbound));
                    }
                    outboundParams.add(i, outbound);
                } catch (Throwable t) {
                    if (throwable == null) {
                        throwable = t;
                    } else if (throwable != t) {
                        throwable.addSuppressed(t);
                    } else {
                        Log.w(TAG, "Duplicate throwable", t);
                    }
                }
            }

            for (Handle h : mAllPredecessors) {
                h.removeSuccessor(this);
            }
            mAllPredecessors.clear();

            mState = STATE_RUNNING;
            if (throwable != null) {
                Log.e(TAG, "Bailing early on "+this+" with exception");
                onResult(new CallResult(throwable));
            } else {
                try {
                    if (localLOGV) {
                        Log.v(TAG, String.format("Callback %s, flags %x", this.asBinder(), mFlags));
                    }
                    // Taint sandbox now, if necessary.
                    sandbox.addTaint(inboundTaints);
                    // Call on resolved SODA.
                    resolvedSoda.call(mFlags, this, outboundParams);
                    outboundParams.clear();
                    mCallParams.clear();
                } catch (Throwable t) {
                    onResult(new CallResult(t));
                }
            }
        }
    }

    @Override
    public void onResult(CallResult result) {
        Sandbox toRelease = null;
        try {
            synchronized (mSync) {
                if (mRunningSandbox != null) {
                    toRelease = mRunningSandbox;
                }
            }
            if (toRelease != null) {
                toRelease.endExecute(this);
            }
            synchronized (mSync) {
                Throwable t = result.getThrowable();
                if (t != null) {
                    Log.e(TAG, "Unhandled exception in "+this, t);
                    for (int i = 0; i < mOutParams.size(); i++) {
                        mOutParams.valueAt(i).onException(mRunningSandbox, t);
                    }
                    mState = STATE_FAILURE;
                    return;
                }

                SparseArray<IBinder> outputs = result.getOutputs();
                mState = STATE_SUCCESS;

                for (int i = 0; i < mOutParams.size(); i++) {
                    int index = mOutParams.keyAt(i);
                    Handle value = mOutParams.valueAt(i);
                    if (outputs.indexOfKey(index) < 0) {
                        Log.wtf(TAG, "Missing expected output handle??");
                        value.onException(mRunningSandbox, new HandleDestroyedException("Missing handle on return ?!?"));
                    }
                    value.onComplete(mRunningSandbox, ISandboxObject.Stub.asInterface(outputs.get(index)));
                }
            }
        } finally {
            if (toRelease != null) {
                OASISApplication.getInstance().putSandbox(toRelease);
            }
            OASISApplication.getInstance().getService().release();
            mSync.open();
            mRunningSandbox = null;
        }
    }

    /*package*/ void waitForReady() {
        synchronized (mSync) {
            mSync.block();
        }
    }

    /*package*/ SparseArray<Handle> getOutHandles() {
        return mOutParams.clone();
    }

    @Override
    public String toString() {
        synchronized (mSync) {
            return String.format("CallRecord{#%d %s %s}", mRecordId,
                    STATE_DESCRIPTIONS[mState], mSoda.getDescriptor());
        }
    }
}

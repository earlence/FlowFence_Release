package edu.umich.oasis.service;

import android.os.ConditionVariable;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.umich.oasis.common.CallParam;
import edu.umich.oasis.common.CallResult;
import edu.umich.oasis.common.Direction;
import edu.umich.oasis.common.HandleDestroyedException;
import edu.umich.oasis.common.IHandle;
import edu.umich.oasis.common.IHandleDebug;
import edu.umich.oasis.common.ParamInfo;
import edu.umich.oasis.common.ParceledPayload;
import edu.umich.oasis.common.ParceledThrowable;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.internal.ISandboxObject;
import edu.umich.oasis.common.ParceledPayloadExceptionResult;

/**
 * Created by jpaupore on 1/26/15.
 */
public class Handle extends IHandle.Stub {
    private static final String TAG = "OASIS.TSHandle";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    // Set of known-immutable types.
    // These can be safely reused across multiple calls.
    private static final Set<String> g_mKnownImmutableTypes;

    static {
        HashSet<String> immut = new HashSet<>();
        immut.add("java.lang.String");
        immut.add("int");
        immut.add("java.lang.Integer");
        immut.add("long");
        immut.add("java.lang.Long");
        immut.add("byte");
        immut.add("java.lang.Byte");
        immut.add("short");
        immut.add("java.lang.Short");
        immut.add("char");
        immut.add("java.lang.Character");
        immut.add("boolean");
        immut.add("java.lang.Boolean");
        immut.add("float");
        immut.add("java.lang.Float");
        immut.add("double");
        immut.add("java.lang.Double");
        immut.add("void");
        immut.add("java.lang.Void");
        g_mKnownImmutableTypes = Collections.unmodifiableSet(immut);
    }

    private enum State {
        PENDING,
        COMPLETE,
        DESTROYED
    };

    private State mState = State.PENDING;

    // Lock: Protects all state.
    // CV: Blocks until computation is complete.
    private final ConditionVariable mSyncRoot = new ConditionVariable();

    private boolean mReleased = false;
    // The set of calls waiting on us.
    // TODO: Should this be weak? Or a set?
    private Set<CallRecord> mSuccessors = new HashSet<>();

    // The CallRecord describing the call. Contains arguments and preds.
    private CallRecord mCallRecord;

    // Used by finished calls.
    // The Sandbox that defined the value.
    private Sandbox mDefiningSandbox = null;
    // The SandboxObjects that are live for this value.
    private PerSandboxMap<ISandboxObject> mLiveValues = new PerSandboxMap<>();
    // The marshaled value.
    private ParceledPayload mMarshaled = null;
    // The taint of the value.
    private TaintSet mTaint = TaintSet.EMPTY;
    // The exception that occurred when running the SODA. Null if no exception.
    private Throwable mThrowable = null;
    private Sandbox.EventHandler mHandler;
    private boolean mValueNull;

    private int mParamIndex;
    private ParamInfo mParamInfo = null;

    /*package*/ Handle(CallRecord record, int index) {
        mCallRecord = record;
        mParamIndex = index;
    }

    private void checkNotDestroyed() {
        if (mState == State.DESTROYED) {
            throw new HandleDestroyedException("Handle destroyed");
        }
    }

    private void checkNotReleased() {
        if (mReleased) {
            throw new HandleDestroyedException("Handle has been released");
        }
    }

    private void checkPending() {
        checkNotDestroyed();
        if (mState == State.COMPLETE) {
            throw new IllegalStateException("Already complete");
        }
    }

    private void checkComplete() {
        checkNotDestroyed();
        if (mState == State.PENDING) {
            throw new IllegalStateException("Not yet complete");
        }
    }

    /**
     * Add a CallRecord that depends on this value.
     * @param succ The CallRecord to add.
     * @param releaseAtomic True to release() atomically with taking this reference.
     * @return True if the CallRecord needs to wait for {@link CallRecord#onDataReady(Handle)} on this Handle,
     * false if it is already completed and/or already referenced by that CallRecord.
     */
    public boolean addSuccessor(CallRecord succ, boolean releaseAtomic) {
        synchronized (mSyncRoot) {
            checkNotReleased();
            checkNotDestroyed();
            if (releaseAtomic) {
                mReleased = true;
            }
            boolean needsWait = mSuccessors.add(succ) && mState == State.PENDING;
            checkForDestroyLocked();
            return needsWait;
        }
    }

    public boolean removeSuccessor(CallRecord succ) {
        synchronized (mSyncRoot) {
            checkNotDestroyed();
            boolean didRemove = mSuccessors.remove(succ);
            checkForDestroyLocked();
            return didRemove;
        }
    }

    public boolean isException() {
        synchronized (mSyncRoot) {
            checkComplete();
            return (mThrowable != null);
        }
    }

    public boolean isMarshalled() {
        synchronized (mSyncRoot) {
            checkComplete();
            return (mMarshaled != null || mValueNull || mThrowable != null);
        }
    }

    public boolean isLiveIn(Sandbox sb) {
        synchronized (mSyncRoot) {
            checkComplete();
            return mLiveValues.containsKey(sb);
        }
    }

    public TaintSet getTaint() {
        synchronized (mSyncRoot) {
            checkComplete();
            return mTaint;
        }
    }

    // About refcounting:
    // All handles start with one reference (the handle returned to the app).
    // When a handle is passed to a later SODA to use it, that CallRecord takes a reference.
    // If, when a SODA is about to execute, there is only 1 reference on it, we can reuse any
    // live copy of it and don't need to worry about marshaling it out.
    // If a handle hits 0 references, we mark it dead.
    // References are 1 ref if not released, +1 for every successor.
    public int getRefCount() {
        synchronized (mSyncRoot) {
            return getRefCountLocked();
        }
    }

    private int getRefCountLocked() {
        if (mState == State.DESTROYED) {
            return 0;
        } else {
            int refcount = mReleased ? 0 : 1;
            refcount += mSuccessors.size();
            return refcount;
        }
    }

    public void onComplete(Sandbox sandbox, ISandboxObject hObj) {
        synchronized (mSyncRoot) {
            checkPending();
            mState = State.COMPLETE;
            mDefiningSandbox = sandbox;
            mValueNull = (hObj == null);
            if (!mValueNull) {
                mLiveValues.put(sandbox, hObj);
                mHandler = new Sandbox.EventHandler() {
                    @Override
                    public boolean onEvent(String event, Sandbox sender, Object args) throws Exception {
                        marshalOut();
                        return true;
                    }
                };
                sandbox.onBeforeDisconnect.register(this, mHandler);
                sandbox.onBeforeTaintAdd.register(this, mHandler);
                sandbox.registerUnmarshalledObject(this, hObj);
            }
            mTaint = sandbox.getTaints();
            mThrowable = null;
            callSuccessorsLocked();
        }
    }

    public void onException(Sandbox sandbox, Throwable t) {
        synchronized (mSyncRoot) {
            checkPending();
            mState = State.COMPLETE;
            mDefiningSandbox = sandbox;
            mThrowable = t;
            mTaint = sandbox.getTaints();
            callSuccessorsLocked();
        }
    }

    private void callSuccessorsLocked() {
        for (CallRecord record : mSuccessors) {
            record.onDataReady(this);
        }
    }

    private void unregisterMarshal() {
        mDefiningSandbox.onBeforeDisconnect.unregister(mHandler);
        mDefiningSandbox.onBeforeTaintAdd.unregister(mHandler);
        mDefiningSandbox.unregisterUnmarshalledObject(this);
    }

    public void marshalOut() {
        synchronized (mSyncRoot) {
            checkComplete();
            if (isMarshalled()) {
                return;
            }
            try {
                ISandboxObject obj = mLiveValues.get(mDefiningSandbox);
                if (obj == null) {
                    // shouldn't have happened - means the sandbox has been destroyed
                    Log.e(TAG, "Can't find live value to marshal out");
                    mThrowable = new DeadObjectException();
                } else {
                    ParceledPayloadExceptionResult result = obj.marshalOut();
                    if (result.isException()) {
                        mThrowable = result.getException();
                    } else {
                        mMarshaled = result.getResult();
                    }
                }
            } catch (Exception e) {
                mThrowable = e;
            }
            if (!mDefiningSandbox.getTaints().isSubsetOf(TaintSet.nullToEmpty(mTaint))) {
                Log.e(TAG, "Sandbox taints have increased prior to marshal out");
                mTaint = mDefiningSandbox.getTaints();
            }
            unregisterMarshal();
            mHandler = null;
        }
    }

    public static CallParam getOutboundCallParam(Sandbox sb, CallParam source, CallRecord record) throws Exception {
        switch (source.getType()) {
            case CallParam.TYPE_NULL:
            case CallParam.TYPE_DATA:
                return source;

            case CallParam.TYPE_HANDLE: {
                int flags = source.getHeader();
                if ((flags & CallParam.HANDLE_SYNC_ONLY) != 0) {
                    return null;
                }
                Handle h = (Handle) source.getPayload();
                return h.getCallParam(sb, flags & CallParam.MASK_FLAG, record);
            }

            default:
                throw new IllegalArgumentException();
        }
    }

    private CallParam getCallParam(Sandbox sb, int flags, CallRecord record) throws Exception {
        CallParam rv = new CallParam();
        synchronized (mSyncRoot) {
            // Can we keep this one alive? We can if we have a live object in that sandbox.
            if (mThrowable != null) {
                ParceledThrowable.throwUnchecked(mThrowable);
            }
            ISandboxObject sbo = mLiveValues.get(sb);
            if (sbo != null) {
                // Do we need to marshal out? We don't if we're the last ones to use this.
                if (isImmutable()) {
                    // We can reuse immutable objects without needing to marshal in and out.
                    // TODO: support @Immutable annotation.
                    if (localLOGV) {
                        Log.v(TAG, "Skipping marshal-out for immutable object " + toString());
                    }
                } else {
                    if (mReleased && mSuccessors.size() == 1 && mSuccessors.contains(record)) {
                        if (localLOGV) {
                            Log.v(TAG, "Skipping marshal-out for discarded object " + toString());
                        }
                        unregisterMarshal();
                    } else {
                        if (localLOGV) {
                            Log.v(TAG, "Marshalling out live object " + toString());
                        }
                        marshalOut();
                    }
                    mLiveValues.remove(sb);
                    flags |= CallParam.HANDLE_RELEASE;
                }
                if (localLOGV) {
                    Log.v(TAG, "Reusing sandbox object for " + toString());
                }
                rv.setHandle(sbo.asBinder(), flags);
            } else {
                // No live value. Marshal if necessary.
                if (localLOGV) {
                    Log.v(TAG, "Marshalling from storage " + toString());
                }
                marshalOut();
                if (mValueNull) {
                    rv.setNull(flags);
                } else {
                    rv.setData(mMarshaled, flags);
                }
            }

            // Last double-check for exceptions, since marshalOut might have thrown them.
            if (mThrowable != null) {
                ParceledThrowable.throwUnchecked(mThrowable);
            }

            return rv;
        }
    }

    private boolean isImmutable() {
        return g_mKnownImmutableTypes.contains(getParamInfo().getTypeName());
    }

    private void checkForDestroyLocked() {
        if (getRefCountLocked() == 0) {
            if (localLOGV) {
                Log.v(TAG, "Destroying "+this, new Exception());
            }
            // no references left - destroy
            if (mDefiningSandbox != null) {
                mDefiningSandbox.onBeforeDisconnect.unregister(mHandler);
                mDefiningSandbox.onBeforeTaintAdd.unregister(mHandler);
                mDefiningSandbox.unregisterUnmarshalledObject(this);
            }
            /*
            // Shouldn't do this - let IBinder ref counting handle this.
            for (ISandboxObject sbo : mLiveValues.values()) {
                try {
                    sbo.destroy();
                } catch (RemoteException re) {
                    Log.e(TAG, "Failed to destroy ISandboxObject", re);
                }
            }
            */
            mCallRecord = null;
            mMarshaled = null;
            mLiveValues = null;
            mDefiningSandbox = null;
            mHandler = null;
            mThrowable = null;
            mSuccessors = null;
            mState = State.DESTROYED;
            mParamInfo = null;
        }
    }

    @Override
    public void release() {
        synchronized (mSyncRoot) {
            if (localLOGV) {
                Log.v(TAG, "Releasing "+this, new Exception());
            }
            mReleased = true;
            checkForDestroyLocked();
            if (localLOGV && getRefCountLocked() > 0) {
                Log.v(TAG, "Still have "+getRefCountLocked()+" successors on "+this);
            }
        }
    }

    @Override
    public Handle withTaint(TaintSet newTaints) {
        // TODO
        return null;
    }

    @Override
    public IHandleDebug getDebug() {
        // TODO
        return null;
    }

    @Override
    public SodaDescriptor getSodaDescriptor() {
        synchronized (mSyncRoot) {
            checkNotDestroyed();
            return mCallRecord.getSODA().getDescriptor();
        }
    }

    @Override
    public int getParamIndex() throws RemoteException {
        synchronized (mSyncRoot) {
            checkNotDestroyed();
            return mParamIndex;
        }
    }

    @Override
    public ParamInfo getParamInfo() {
        synchronized (mSyncRoot) {
            checkNotDestroyed();
            if (mParamInfo == null) {
                SodaRef soda = mCallRecord.getSODA();
                if (mParamIndex == CallResult.RETURN_VALUE) {
                    mParamInfo = new ParamInfo(soda.getResultType(), mParamIndex, Direction.OUT);
                } else {
                    mParamInfo = soda.getParamInfo().get(mParamIndex);
                }
            }
            return mParamInfo;
        }
    }

    @Override
    public ParceledPayloadExceptionResult tryDeclassify(boolean mergeTaints) {
        // TODO
        return null;
    }

    @Override
    public boolean isComplete() {
        synchronized (mSyncRoot) {
            checkNotDestroyed();
            return (mState == State.COMPLETE);
        }
    }

    @Override
    public boolean tryWaitForComplete() {
        // TODO deadlock prevention
        mCallRecord.waitForReady();
        return true;
    }

    @Override
    public String toString() {
        return String.format("Handle[%s]{%s}", getParamInfo(), mCallRecord);
    }
}

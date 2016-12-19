package edu.umich.oasis.service;

import android.util.Log;

import java.util.Collections;
import java.util.List;

import edu.umich.oasis.common.CallFlags;
import edu.umich.oasis.common.CallParam;
import edu.umich.oasis.common.CallResult;
import edu.umich.oasis.common.ISoda;
import edu.umich.oasis.common.ParamInfo;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.SodaDetails;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.internal.IResolvedSoda;

/**
 * Created by jpaupore on 2/1/15.
 */
public class SodaRef extends ISoda.Stub {
    private static final String TAG = "OASIS.SODA";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private final PerSandboxMap<IResolvedSoda> mResolved;
    private final OASISApplication mApplication;

    private final SodaDescriptor mDescriptor;
    private final String mResultType;
    private final List<ParamInfo> mParamInfo;
    private final TaintSet mRequiredTaints;
    private final TaintSet mOptionalTaints;

    public SodaRef(SodaDescriptor desc, boolean bestMatch, SodaDetails details) throws Exception {
        mResolved = new PerSandboxMap<>();
        mApplication = OASISApplication.getInstance();

        Sandbox sbResolve = mApplication.getSandboxForResolve(desc.definingClass.getPackageName());
        try {
            if (details == null) {
                details = new SodaDetails();
            }
            IResolvedSoda resolved = sbResolve.resolve(desc, bestMatch, details);
            mResolved.put(sbResolve, resolved);

            mDescriptor = details.descriptor;
            mResultType = details.resultType;
            mParamInfo = details.paramInfo;
            mRequiredTaints = details.requiredTaints;
            mOptionalTaints = details.optionalTaints;
        } finally {
            mApplication.putSandbox(sbResolve);
        }
    }

    /*package*/ final IResolvedSoda resolveFor(Sandbox sandbox) throws Exception {
            IResolvedSoda resolvedSoda = mResolved.get(sandbox);
            if (resolvedSoda == null) {
                synchronized (this) {
                    resolvedSoda = mResolved.get(sandbox);
                    if (resolvedSoda == null) {
                        if (localLOGV) {
                            Log.v(TAG, "Resolving "+mDescriptor);
                        }
                        resolvedSoda = sandbox.resolve(mDescriptor, false, null);
                        mResolved.put(sandbox, resolvedSoda);
                        if (localLOGV) {
                            Log.v(TAG, "Resolved "+mDescriptor);
                        }
                    }
                }
            }
            return resolvedSoda;
    }

    /*package*/ final boolean isResolvedIn(Sandbox sandbox) {
        return mResolved.containsKey(sandbox);
    }

    /*package*/ final void forget(Sandbox sandbox) {
        mResolved.remove(sandbox);
    }


    @Override
    public String getResultType() {
        return mResultType;
    }

    @Override
    public SodaDescriptor getDescriptor() {
        return mDescriptor;
    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return mParamInfo;
    }

    @Override
    public TaintSet getRequiredTaints() {
        return mRequiredTaints;
    }

    @Override
    public TaintSet getOptionalTaints() {
        return mOptionalTaints;
    }

    @Override
    public void getDetails(SodaDetails details) {
        if (details != null) {
            details.descriptor = mDescriptor;
            details.resultType = mResultType;
            details.paramInfo = mParamInfo;
            details.requiredTaints = mRequiredTaints;
            details.optionalTaints = mOptionalTaints;
        }
    }

    @Override
    public CallResult call(int flags, List<CallParam> params, TaintSet extraTaint) {
        try {
            // Start by setting up a call record.
            CallRecord record = new CallRecord(this, flags, params, extraTaint);

            if ((flags & CallFlags.CALL_ASYNC) != CallFlags.CALL_ASYNC) {
                record.waitForReady();
            }

            return new CallResult(record.getOutHandles());
        } catch (Throwable t) {
            return new CallResult(t);
        }
    }
}

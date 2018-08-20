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

package edu.umich.flowfence.client;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import org.apache.commons.lang3.ClassUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.umich.flowfence.common.CallFlags;
import edu.umich.flowfence.common.CallParam;
import edu.umich.flowfence.common.CallResult;
import edu.umich.flowfence.common.Direction;
import edu.umich.flowfence.common.IHandle;
import edu.umich.flowfence.common.ISoda;
import edu.umich.flowfence.common.OASISConstants;
import edu.umich.flowfence.common.ParamInfo;
import edu.umich.flowfence.common.SodaDescriptor;
import edu.umich.flowfence.common.SodaDetails;
import edu.umich.flowfence.common.TaintSet;

public class Soda<TResult> {
    private static final String TAG = "OASIS.Client";

    private static <T> Class<? extends T> checkClass(String className, Class<T> clazz, ClassLoader loader)
            throws ClassNotFoundException {
        Class<?> resultClazz;
        if ("void".equals(className)) {
            if ("void".equals(clazz.getName())) {
                return clazz;
            } else if ("java.lang.Void".equals(clazz.getName())) {
                return clazz;
            } else {
                throw new ClassCastException("Void type in non-void context");
            }
        }
        resultClazz = ClassUtils.getClass(loader, className, true);

        // Special handling for primitives.
        // If we can be handled by one of the primitive conversions, allow it.
        if (resultClazz.isPrimitive()) {
            if (ClassUtils.isAssignable(resultClazz, clazz, true)) {
                return clazz;
            } else {
                throw new ClassCastException("Cannot convert "+className+" to "+clazz.getName());
            }
        }

        return resultClazz.asSubclass(clazz);
    }

    private final Class<? extends TResult> resultClass;
    private final List<ParamInfo> paramInfo;
    private final Class<?>[] paramClasses;
    private final ISoda hSoda;
    private final int numOutExpected;
    //private final Class<?> declaringClass;
    private final SodaDescriptor descriptor;
    private final ClassLoader loader;

    public Soda(ISoda hSoda, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
        this(hSoda, null, clazz);
    }

    public Soda(ISoda hSoda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
        this(hSoda, details, clazz, Soda.class.getClassLoader());
    }

    public Soda(ISoda hSoda, Class<TResult> clazz, ClassLoader loader) throws RemoteException, ClassNotFoundException {
        this(hSoda, null, clazz, loader);
    }

    public Soda(ISoda hSoda, SodaDetails details, Class<TResult> clazz, ClassLoader loader) throws RemoteException, ClassNotFoundException {
        this.hSoda = hSoda;

        if (details == null) {
            details = new SodaDetails();
            hSoda.getDetails(details);
        }

        // First, let's see if this actually is compatible.
        // This will throw ClassNotFound or ClassCast if we aren't.
        this.loader = loader;
        resultClass = checkClass(hSoda.getResultType(), clazz, loader);
        // Then, fill in param info.
        paramInfo = Collections.unmodifiableList(hSoda.getParamInfo());
        paramClasses = new Class<?>[paramInfo.size()];
        // Find the number of out params we normally have.
        int numOut = 0;
        for (int i = 0; i < paramInfo.size(); i++) {
            ParamInfo info = paramInfo.get(i);
            if (info.getDirection().isOut()) {
                numOut++;
            }
            paramClasses[i] = info.getType(loader);
        }
        numOutExpected = numOut;
        // Fill in the other info now.
        descriptor = hSoda.getDescriptor();
    }

	public Sealed<TResult> invoke(Object... args) throws RemoteException {
	    ArgBuilderImpl<?,?> builder = new ArgBuilderImpl<>();
        return builder.buildInvoke(args).call();
	}

	public Class<?> getDeclaringClass() throws ClassNotFoundException {
		return getDeclaringClass(loader);
	}

    public Class<?> getDeclaringClass(ClassLoader loader) throws ClassNotFoundException {
        return ClassUtils.getClass(loader, descriptor.definingClass.getClassName(), true);
    }
	
	public SodaDescriptor getDescriptor() {
        return descriptor;
    }

    public List<ParamInfo> getParamInfo() {
        return paramInfo;
    }

    public Class<? extends TResult> getResultType() {
        return resultClass;
    }

    public ISoda getRawHandle() {
        return hSoda;
    }

    /**
     * Beat the ever-loving hell out of Java's generics system to add some type safety to the client.
     * @param <TArg> The type of the argument that's being passed in.
     * @param <TRest> The type of the next builder - ArgBuilder, if there are more arguments to bind,
     *               or CallRunner, if all the arguments are set up.
     */
    protected class ArgBuilderImpl<TArg, TRest extends CallBuilder> implements ArgBuilder<TArg, TRest> {
        private List<CallParam> callParams;
        private SparseArray<Sealed<?>> outRefs;
        private int argno;
        private final int nargs;
        private ParamInfo currentParamInfo;
        private Class<?> currentParamClass;

        public ArgBuilderImpl() {
            nargs = paramInfo.size();
            argno = 0;
            callParams = new ArrayList<>(nargs);
            outRefs = new SparseArray<>(numOutExpected);
            currentParamInfo = paramInfo.get(0);
            currentParamClass = paramClasses[0];
        }

        @SuppressWarnings("unchecked")
        private TRest next() {
            if (++argno >= nargs) {
                argno = nargs;
                return (TRest)new CallRunnerImpl(callParams, outRefs);
            } else {
                currentParamInfo = paramInfo.get(argno);
                currentParamClass = paramClasses[argno];
                return (TRest)this;
            }
        }

        /*
        private void checkClass(Class<?> tIn, Class<?> tOut, Direction dir) {
            Class<?> tArg = currentParamClass;
            if (dir.isIn() && tIn != null && !ClassUtils.isAssignable(tIn, tArg, true)) {
                throw new ClassCastException("Cannot assign on marshal-in");
            }
            if (dir.isOut()) {
                Class<?> tReturn = (dir == Direction.INOUT) ? tIn : tArg;
                if (tReturn != null && !ClassUtils.isAssignable(tReturn, tOut, true)) {
                    throw new ClassCastException("Cannot assign on marshal-out");
                }
            }
        }
        */

        private <TIn extends TArg> TRest doData(TIn arg, Sealed<? super TIn> hDest, Direction direction) {
            //checkClass((arg != null) ? arg.getClass() : null,
            //        (hDest != null) ? hDest.getSealedClass() : null, direction);
            CallParam param = new CallParam();
            int flags = 0;
            if (direction.isOut()) {
                flags |= CallParam.FLAG_RETURN;
                outRefs.append(argno, hDest);
            }
            if (direction == Direction.REFINOUT)  {
                flags |= CallParam.FLAG_BY_REF;
            }
            param.setData(arg, flags);
            callParams.add(param);
            return next();
        }

        private <TIn extends TArg> TRest doHandle(Sealed<TIn> hArg, Sealed<? super TIn> hDest, Direction direction) {
            // will throw NPE if hArg is null
            //checkClass((hArg != null) ? hArg.getSealedClass() : null,
            //        (hDest != null) ? hDest.getSealedClass() : null, direction);
            CallParam param = new CallParam();
            int flags = 0;
            IBinder handle = null;
            if (direction.isIn()) {
                handle = hArg.getHandle().asBinder();
            }
            if (direction.isOut()) {
                flags |= CallParam.FLAG_RETURN;
                if (handle != null) {
                    flags |= CallParam.HANDLE_RELEASE;
                }
                outRefs.append(argno, hDest);
            }
            if (direction == Direction.REFINOUT)  {
                flags |= CallParam.FLAG_BY_REF;
            }
            param.setHandle(handle, flags);
            callParams.add(param);
            return next();
        }

        @Override
        public TRest argNull() {
            return inNull();
        }

        @Override
        public TRest inNull() {
            CallParam param = new CallParam();
            param.setNull(0);
            callParams.add(param);
            return next();
        }

        @Override
        public TRest arg(TArg arg) {
            return doData(arg, null, Direction.IN);
        }

        @Override
        public <TIn extends TArg> TRest arg(Sealed<TIn> hArg) {
            Direction d = currentParamInfo.getDirection();
            if (d == Direction.OUT || d == Direction.REFINOUT) {
                throw new IllegalArgumentException("Out and ref inout parameters must be explicit");
            }
            return doHandle(hArg, hArg, d);
        }

        @Override
        public TRest in(TArg arg) {
            return doData(arg, null, Direction.IN);
        }

        @Override
        public <TIn extends TArg> TRest in(Sealed<TIn> hArg) {
            return doHandle(hArg, null, Direction.IN);
        }

        @Override
        public <TIn extends TArg> TRest inOut(TIn arg, Sealed<? super TIn> dest) {
            return doData(arg, dest, Direction.INOUT);
        }

        @Override
        public <TIn extends TArg> TRest inOut(Sealed<TIn> hArg, Sealed<? super TIn> dest) {
            return doHandle(hArg, dest, Direction.INOUT);
        }

        @Override
        public TRest refInOut(TArg arg, Sealed<? super TArg> dest) {
            return doData(arg, dest, Direction.REFINOUT);
        }

        @Override
        public TRest refInOut(Sealed<TArg> hArg) {
            return doHandle(hArg, hArg, Direction.REFINOUT);
        }

        @Override
        public TRest refInOut(Sealed<? extends TArg> hArg, Sealed<? super TArg> dest) {
            return doHandle(hArg, dest, Direction.REFINOUT);
        }

        @Override
        public TRest out(Sealed<? super TArg> hArg) {
            return doHandle(null, hArg, Direction.OUT);
        }

        @Override
        public <TIn extends TArg> TRest inOut(Sealed<TIn> hArg) {
            return doHandle(hArg, hArg, Direction.INOUT);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<TArg> getArgClass() {
            try {
                return (Class<TArg>) currentParamInfo.getType(loader);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        /*package*/ CallRunnerImpl buildInvoke(Object... args) {
            for (Object arg : args) {
                if (arg instanceof Sealed<?>) {
                    this.arg((Sealed<TArg>)arg);
                } else {
                    this.arg((TArg)arg);
                }
            }
            return (CallRunnerImpl)next();
        }
    }

    protected class CallRunnerImpl implements CallRunner<TResult> {
        private List<CallParam> callParams;
        private SparseArray<Sealed<?>> outRefs;
        private TaintSet.Builder taints;
        private int flags = 0;

        public CallRunnerImpl() {
            callParams = new ArrayList<>();
            outRefs = new SparseArray<>();
        }
        public CallRunnerImpl(List<CallParam> params, SparseArray<Sealed<?>> outs) {
            callParams = params;
            outRefs = outs;
        }

        private IHandle execRemote() throws RemoteException {
            CallResult result = hSoda.call(flags, callParams, (taints == null) ? TaintSet.EMPTY : taints.build());
            // Handle out refs.
            IHandle hReturn = null;
            // This throws RuntimeException if the call failed in a
            // manner that should be propagated back to the caller.
            SparseArray<IBinder> outResults = result.getOutputs();
            for (int i = 0; i < outResults.size(); i++) {
                int index = outResults.keyAt(i);
                IHandle handle = IHandle.Stub.asInterface(outResults.valueAt(i));
                if (index == CallResult.RETURN_VALUE) {
                    hReturn = handle;
                    continue;
                }
                Sealed<?> sealed = outRefs.get(index);
                if (sealed != null) {
                    sealed.setHandle(handle);
                } else {
                    Log.w("OASIS.Client", "Ignoring unwanted out handle");
                    handle.release();
                }
            }
            return hReturn;
        }

        @Override
        public Sealed<TResult> call() throws RemoteException {
            if (resultClass == void.class || resultClass == Void.class) {
                // Void responses will always be null, so we can safely
                // ask for the return value to be ignored.
                flags |= CallFlags.NO_RETURN_VALUE;
            }
            IHandle hReturn = execRemote();
            return new Sealed<>(hReturn);
        }

        @Override
        public void run() throws RemoteException {
            flags |= CallFlags.NO_RETURN_VALUE;
            execRemote();
        }

        @Override
        public CallRunnerImpl after(Sealed<?>... syncHandles) {
            for (Sealed<?> sync : syncHandles) {
                CallParam param = new CallParam();
                param.setHandle(sync.getHandle().asBinder(), CallParam.HANDLE_SYNC_ONLY);
                callParams.add(param);
            }
            return this;
        }

        private synchronized TaintSet.Builder getTaints() {
            if (taints == null) {
                taints = new TaintSet.Builder();
            }
            return taints;
        }

        @Override
        public CallRunner<TResult> taintedWith(TaintSet taint) {
            getTaints().unionWith(taint);
            return this;
        }

        @Override
        public CallRunner<TResult> taintedWith(String taint) {
            getTaints().addTaint(taint);
            return this;
        }

        @Override
        public CallRunner<TResult> forceSandbox(int sandbox) {
            if (sandbox < 0 || sandbox >= OASISConstants.NUM_SANDBOXES) {
                throw new IndexOutOfBoundsException();
            }
            flags |= (CallFlags.OVERRIDE_SANDBOX | (sandbox & CallFlags.SANDBOX_NUM_MASK));
            return this;
        }

        @Override
        public CallRunner<TResult> asAsync() {
            flags |= CallFlags.CALL_ASYNC;
            return this;
        }

        @Override
        public Class<? extends TResult> getResultClass() {
            return resultClass;
        }
    }

    public static final class S0<TResult> extends Soda<TResult> implements CallRunner<TResult> {
        /*package*/ S0(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
        @Override
        public Sealed<TResult> call() throws RemoteException {
            return new CallRunnerImpl().call();
        }

        @Override
        public void run() throws RemoteException {
            new CallRunnerImpl().run();
        }

        @Override
        public CallRunnerImpl after(Sealed<?>... syncHandles) {
            return new CallRunnerImpl().after(syncHandles);
        }

        @Override
        public CallRunner<TResult> taintedWith(TaintSet taint) {
            return new CallRunnerImpl().taintedWith(taint);
        }

        @Override
        public CallRunner<TResult> taintedWith(String taint) {
            return new CallRunnerImpl().taintedWith(taint);
        }

        @Override
        public CallRunner<TResult> forceSandbox(int sandbox) {
            return new CallRunnerImpl().forceSandbox(sandbox);
        }

        @Override
        public CallRunner<TResult> asAsync() {
            return new CallRunnerImpl().asAsync();
        }

        @Override
        public Class<? extends TResult> getResultClass() {
            return getResultType();
        }
    }

    public static class SArgBase<TResult, TFirst, TRest extends CallBuilder>
            extends Soda<TResult>
            implements ArgBuilder<TFirst, TRest> {
        protected SArgBase(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }

        private ArgBuilder<TFirst, TRest> getBuilder() {
            return new ArgBuilderImpl<>();
        }

        @Override
        public TRest in(TFirst tFirst) {
            return getBuilder().in(tFirst);
        }

        @Override
        public <TIn extends TFirst> TRest in(Sealed<TIn> hArg) {
            return getBuilder().in(hArg);
        }

        @Override
        public TRest out(Sealed<? super TFirst> hArg) {
            return getBuilder().out(hArg);
        }

        @Override
        public <TIn extends TFirst> TRest inOut(TIn arg, Sealed<? super TIn> dest) {
            return getBuilder().inOut(arg, dest);
        }

        @Override
        public <TIn extends TFirst> TRest inOut(Sealed<TIn> hArg) {
            return getBuilder().inOut(hArg);
        }

        @Override
        public <TIn extends TFirst> TRest inOut(Sealed<TIn> hArg, Sealed<? super TIn> dest) {
            return getBuilder().inOut(hArg, dest);
        }

        @Override
        public TRest refInOut(TFirst tFirst, Sealed<? super TFirst> dest) {
            return getBuilder().refInOut(tFirst, dest);
        }

        @Override
        public TRest refInOut(Sealed<TFirst> hArg) {
            return getBuilder().refInOut(hArg);
        }

        @Override
        public TRest refInOut(Sealed<? extends TFirst> hArg, Sealed<? super TFirst> dest) {
            return getBuilder().refInOut(hArg, dest);
        }

        @Override
        public TRest arg(TFirst tFirst) {
            return getBuilder().arg(tFirst);
        }

        @Override
        public <TIn extends TFirst> TRest arg(Sealed<TIn> hArg) {
            return getBuilder().arg(hArg);
        }

        @Override
        public TRest argNull() {
            return getBuilder().argNull();
        }

        @Override
        public TRest inNull() {
            return getBuilder().inNull();
        }

        @Override
        public Class<TFirst> getArgClass() {
            return getBuilder().getArgClass();
        }
    }

    public static final class S1<T1, TResult> extends SArgBase<TResult,
            T1, CallRunner<TResult>> {
        /*package*/ S1(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S2<T1, T2, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, CallRunner<TResult>>> {
        /*package*/ S2(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S3<T1, T2, T3, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, ArgBuilder<T3, CallRunner<TResult>>>> {
        /*package*/ S3(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S4<T1, T2, T3, T4, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, ArgBuilder<T3, ArgBuilder<T4, CallRunner<TResult>>>>> {
        /*package*/ S4(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S5<T1, T2, T3, T4, T5, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, ArgBuilder<T3, ArgBuilder<T4, ArgBuilder<T5, CallRunner<TResult>>>>>> {
        /*package*/ S5(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S6<T1, T2, T3, T4, T5, T6, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, ArgBuilder<T3, ArgBuilder<T4, ArgBuilder<T5,
                ArgBuilder<T6, CallRunner<TResult>>>>>>> {
        /*package*/ S6(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S7<T1, T2, T3, T4, T5, T6, T7, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, ArgBuilder<T3, ArgBuilder<T4, ArgBuilder<T5,
                ArgBuilder<T6, ArgBuilder<T7, CallRunner<TResult>>>>>>>> {
        /*package*/ S7(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }

    public static final class S8<T1, T2, T3, T4, T5, T6, T7, T8, TResult> extends SArgBase<TResult,
            T1, ArgBuilder<T2, ArgBuilder<T3, ArgBuilder<T4, ArgBuilder<T5,
                ArgBuilder<T6, ArgBuilder<T7, ArgBuilder<T8, CallRunner<TResult>>>>>>>>> {
        /*package*/ S8(ISoda soda, SodaDetails details, Class<TResult> clazz) throws RemoteException, ClassNotFoundException {
            super(soda, details, clazz);
        }
    }
}

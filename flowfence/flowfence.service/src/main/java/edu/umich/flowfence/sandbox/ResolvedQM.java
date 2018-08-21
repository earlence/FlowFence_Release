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

package edu.umich.flowfence.sandbox;

import android.os.BadParcelableException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import org.apache.commons.lang3.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import edu.umich.flowfence.annotations.InOut;
import edu.umich.flowfence.annotations.MayContain;
import edu.umich.flowfence.annotations.PreserveThis;
import edu.umich.flowfence.annotations.TaintedWith;
import edu.umich.flowfence.common.CallFlags;
import edu.umich.flowfence.common.CallParam;
import edu.umich.flowfence.common.CallResult;
import edu.umich.flowfence.common.Direction;
import edu.umich.flowfence.common.ParamInfo;
import edu.umich.flowfence.common.ParceledPayload;
import edu.umich.flowfence.common.QMDescriptor;
import edu.umich.flowfence.common.QMDetails;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.internal.IQMCallback;
import edu.umich.flowfence.internal.IResolvedQM;

/*package*/ final class ResolvedQM extends IResolvedQM.Stub {
    private static final String TAG = "FlowFence.QM";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private interface Caller {
        Object call(Object... args) throws Exception;
    }

    private final SandboxService mSandbox;
    private final QMDescriptor mOriginalDescriptor;
    private final SandboxContext mContext;
    private final Class<?> mDefiningClass;
    private final MemberData<?> mMemberData;

    private Class<?>[] classNamesToClasses(List<String> classNames, ClassLoader loader) throws Exception {
        Class<?>[] classes = new Class<?>[classNames.size()];
        for (int i = 0; i < classNames.size(); i++) {
            classes[i] = ClassUtils.getClass(loader, classNames.get(i));
        }
        return classes;
    }

    protected static <TAnnot extends Annotation, TMember extends AnnotatedElement & Member>
    TAnnot getMatchingAnnotation(Class<TAnnot> annotClass, TMember member) {
        TAnnot annot = member.getAnnotation(annotClass);
        if (annot != null) {
            return annot;
        }

        Class<?> declaringClass = member.getDeclaringClass();
        annot = declaringClass.getAnnotation(annotClass);
        if (annot != null) {
            return annot;
        }

        Package declaringPackage = declaringClass.getPackage();
        annot = declaringPackage.getAnnotation(annotClass);
        if (annot != null) {
            return annot;
        }

        return null;
    }

    private abstract class MemberData<TElem extends AnnotatedElement & Member> {
        protected final TElem element;
        private QMDetails details = null;

        protected MemberData(TElem element) {
            this.element = element;
        }

        public abstract Object call(Object... args) throws Exception;

        public synchronized final QMDetails getDetails() {
            if (details == null) {
                details = new QMDetails();
                fillInDetails();
            }
            return details;
        }

        private void fillInDetails() {
            details.descriptor = getDescriptor();
            details.resultType = getReturnType().getName();

            details.paramInfo = new ArrayList<>(countParameters());
            fillInParamInfo(details.paramInfo);

            TaintedWith taintedWith = getMatchingAnnotation(TaintedWith.class, element);
            if (taintedWith != null) {
                details.requiredTaints = TaintSet.fromStrings(Arrays.asList(taintedWith.value()));
            } else {
                details.requiredTaints = TaintSet.EMPTY;
            }

            MayContain mayContain = getMatchingAnnotation(MayContain.class, element);
            if (mayContain != null) {
                details.optionalTaints = TaintSet.fromStrings(Arrays.asList(mayContain.value()));
            } else {
                details.optionalTaints = TaintSet.EMPTY;
            }
        }

        public int countParameters() {
            return getDeclaredParameterTypes().length;
        }
        public abstract Class<?> getReturnType();

        protected abstract Class<?>[] getDeclaredParameterTypes();
        protected abstract Annotation[][] getDeclaredAnnotations();
        public QMDescriptor getDescriptor() {
            return mOriginalDescriptor;
        }

        protected void fillInParamInfo(List<ParamInfo> paramInfo) {
            Class<?>[] paramTypes = getDeclaredParameterTypes();
            Annotation[][] annotations = getDeclaredAnnotations();

            final int offset = paramInfo.size();

            for (int i = 0; i < paramTypes.length; i++) {
                Direction d = Direction.IN;
                for (int j = 0; j < annotations[i].length; j++) {
                    if (annotations[i][j] instanceof InOut) {
                        d = Direction.INOUT;
                    }
                }
                paramInfo.add(new ParamInfo(paramTypes[i].getName(), i+offset, d));
            }
        }
    }

    private class MethodData extends MemberData<Method> {
        public MethodData(Method element) {
            super(element);
        }

        @Override
        public Object call(Object... args) throws Exception {
            return element.invoke(null, args);
        }

        @Override
        protected Class<?>[] getDeclaredParameterTypes() {
            return element.getParameterTypes();
        }

        @Override
        protected Annotation[][] getDeclaredAnnotations() {
            return element.getParameterAnnotations();
        }

        @Override
        public Class<?> getReturnType() {
            return element.getReturnType();
        }

        @Override
        public QMDescriptor getDescriptor() {
            return QMDescriptor.forMethod(mContext, element);
        }
    }

    private class InstanceMethodData extends MethodData {
        public InstanceMethodData(Method element) {
            super(element);
        }

        @Override
        public Object call(Object... args) throws Exception {
            if (args.length == 0) {
                throw new IllegalArgumentException("Not enough arguments");
            }
            Object[] methodArgs = Arrays.copyOfRange(args, 1, args.length);
            return element.invoke(args[0], methodArgs);
        }

        @Override
        public int countParameters() {
            return super.countParameters()+1;
        }

        @Override
        protected void fillInParamInfo(List<ParamInfo> paramInfo) {
            Direction dir = Direction.INOUT;
            PreserveThis preserveThis = element.getAnnotation(PreserveThis.class);
            if (preserveThis != null && preserveThis.value()) {
                dir = Direction.IN;
            }
            paramInfo.add(new ParamInfo(element.getDeclaringClass().getName(), 0, dir));

            super.fillInParamInfo(paramInfo);
        }
    }

    private class ConstructorData<T> extends MemberData<Constructor<T>> {
        public ConstructorData(Constructor<T> element) {
            super(element);
        }

        @Override
        public Object call(Object... args) throws Exception {
            return element.newInstance(args);
        }

        @Override
        protected Class<?>[] getDeclaredParameterTypes() {
            return element.getParameterTypes();
        }

        @Override
        protected Annotation[][] getDeclaredAnnotations() {
            return element.getParameterAnnotations();
        }

        @Override
        public Class<?> getReturnType() {
            return element.getDeclaringClass();
        }

        @Override
        public QMDescriptor getDescriptor() {
            return QMDescriptor.forConstructor(mContext, element);
        }
    }

    private InstanceMethodData resolveInstance(Class<?> definingClass, String methodName,
                                       Class<?>[] paramClasses, boolean bestMatch) throws Exception {
        if (localLOGD) {
            Log.d(TAG, "Resolving as instance");
        }
        final Method method = definingClass.getMethod(methodName, paramClasses);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Method is static, but was resolved as instance");
        }

        return new InstanceMethodData(method);
    }

    private MethodData resolveStatic(Class<?> definingClass, String methodName,
                                     Class<?>[] paramClasses, boolean bestMatch) throws Exception {
        if (localLOGD) {
            Log.d(TAG, "Resolving as static");
        }
        final Method method = definingClass.getMethod(methodName, paramClasses);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Method is instance, but was resolved as static");
        }

        return new MethodData(method);
    }

    private <T> ConstructorData<T> resolveConstructor(
            Class<T> definingClass, Class<?>[] paramClasses, boolean bestMatch) throws Exception {

        if (localLOGD) {
            Log.d(TAG, "Resolving as ctor");
        }
        final Constructor<T> ctor = definingClass.getConstructor(paramClasses);

        return new ConstructorData<>(ctor);
    }

    public ResolvedQM(SandboxService sandbox, QMDescriptor descriptor, boolean bestMatch) throws Exception {
        //if (localLOGD) {
            Log.d(TAG, "Resolving " + descriptor.toString());
        //}
        mSandbox = sandbox;
        mContext = mSandbox.getContextForPackage(descriptor.definingClass.getPackageName());
        //if (localLOGD) {
            Log.d(TAG, "Got package context");
        //}
        final ClassLoader loader = mContext.getClassLoader();

        mDefiningClass = ClassUtils.getClass(loader, descriptor.definingClass.getClassName(), true);

        Class<?>[] paramClasses = classNamesToClasses(descriptor.paramTypes, loader);

        switch (descriptor.kind) {
            case QMDescriptor.KIND_INSTANCE:
                mMemberData = resolveInstance(mDefiningClass, descriptor.methodName, paramClasses, bestMatch);
                break;
            case QMDescriptor.KIND_STATIC:
                mMemberData = resolveStatic(mDefiningClass, descriptor.methodName, paramClasses, bestMatch);
                break;
            case QMDescriptor.KIND_CTOR:
                mMemberData = resolveConstructor(mDefiningClass, paramClasses, bestMatch);
                break;
            default:
                throw new IllegalArgumentException("Unknown QMDescriptor type");
        }

        // Check that return type can be marshalled.
        if (!ParceledPayload.canParcelType(mMemberData.getReturnType(), true)) {
            throw new BadParcelableException("Cannot parcel type "+mMemberData.getReturnType().getName());
        }

        mOriginalDescriptor = descriptor;
    }

    public String getResultType() {
        return mMemberData.getReturnType().getName();
    }

    @Override
    public final void getDetails(QMDetails details) {
        if (details != null) {
            details.copyFrom(mMemberData.getDetails());
        }
    }

    private Object unpack(CallParam param) throws RemoteException {
        if (param == null) {
            return null;
        }

        switch (param.getType()) {
            case CallParam.TYPE_NULL: {
                return null;
            }

            case CallParam.TYPE_DATA: {
                return param.decodePayload(mContext.getClassLoader());
            }

            case CallParam.TYPE_HANDLE: {
                IBinder handle = (IBinder)param.getPayload();
                boolean shouldRelease = (param.getHeader() & CallParam.HANDLE_RELEASE) != 0;
                return SandboxObject.objectForBinder(handle, mContext.getClassLoader(), shouldRelease);
            }

            default: {
                throw new IllegalArgumentException("Bad CallParam type");
            }
        }
    }

    @Override
    public void call(int flags, IQMCallback callback, List<CallParam> params) throws RemoteException {
        try {
            if (localLOGD) {
                Log.d(TAG, String.format("Incoming sandbox call for %s, %d parameters:",
                                         mOriginalDescriptor, params.size()));
                for (CallParam param : params) {
                    Log.d(TAG, param.toString(mContext.getClassLoader()));
                }
            }
            if (localLOGV) {
                Log.v(TAG, String.format("Callback %s, flags %d", callback, flags));
            }
            // Sanity check.
            final int numParams = params.size();
            if (numParams != mMemberData.countParameters()) {
                throw new IllegalArgumentException("Wrong number of arguments supplied");
            }

            boolean hasReturn = (flags & CallFlags.NO_RETURN_VALUE) == 0;

            final ArrayList<Object> args = new ArrayList<>();
            final SparseArray<IBinder> outs = new SparseArray<>();

            mContext.beginQM();
            try {
                if (hasReturn) {
                    outs.append(CallResult.RETURN_VALUE, null);
                }

                for (int i = 0; i < numParams; i++) {
                    CallParam param = params.get(i);
                    int paramHeader = param.getHeader();
                    if (param.getType() == CallParam.TYPE_HANDLE &&
                            (paramHeader & CallParam.HANDLE_SYNC_ONLY) != 0) {
                        Log.w(TAG, "HANDLE_SYNC_ONLY in sandbox for " + mOriginalDescriptor);
                        continue;
                    }
                    // Deserialize argument, marshaling as necessary.
                    Object arg = unpack(param);
                    // TODO: FLAG_BY_REF
                    args.add(arg);
                    // Put together the out parameter for inout params.
                    if ((paramHeader & CallParam.FLAG_RETURN) != 0) {
                        if (localLOGV) {
                            Log.v(TAG, String.format("Adding out param %d", i));
                        }
                        outs.append(i, SandboxObject.binderForObject(this, arg));
                    }
                }

                // Actually do the call.
                Object[] argArray = args.toArray();
                if (localLOGD) {
                    Log.d(TAG, "Preparing to call " + mOriginalDescriptor.printCall(argArray));
                }

                Object retval = mMemberData.call(argArray);

                if (localLOGD) {
                    Log.d(TAG, "Call returned: " + Objects.toString(retval));
                }

                // Bundle up handle for return value.
                if (hasReturn) {
                    IBinder retvalObj = SandboxObject.binderForObject(this, retval);
                    outs.put(CallResult.RETURN_VALUE, retvalObj);
                }

                // DEBUG: print out params
                if (localLOGV) {
                    for (int i = 0; i < outs.size(); i++) {
                        Log.v(TAG, String.format("out[%d] = %s", outs.keyAt(i), outs.valueAt(i)));
                    }
                }
                // Post results to caller.
                if (localLOGD) {
                    Log.d(TAG, "Posting results to caller");
                }
                callback.onResult(new CallResult(outs));
            } catch (InvocationTargetException ioe) {
                Throwable t = ioe.getTargetException();
                if (t instanceof Exception) {
                    throw ((Exception)t);
                }
                throw ioe;
            } finally {
                // Clear our ambient context.
                if (localLOGD) {
                    Log.d(TAG, "Clearing call token");
                }
                mContext.endQM();
            }
        } catch (Exception e) {
            //Log.e(TAG, String.format("Error invoking %s", mOriginalDescriptor), e);
            callback.onResult(new CallResult(e));
        }
    }

    @Override
    public String toString() {
        return String.format("ResolvedQM[%s]", mOriginalDescriptor);
    }
}

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

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import org.apache.commons.lang3.ClassUtils;

import edu.umich.flowfence.common.*;

public final class Sealed<T> implements Cloneable {
	private IHandle handle;
    private IHandleDebug debug;

    public static <T> Sealed<T> empty() {
        return new Sealed<>();
    }

    public static <T> Sealed<T> wrap(IHandle handle, Class<T> refClass) {
        try {
            ParamInfo pi = handle.getParamInfo();
            Class<?> declaredClass = pi.getType(refClass.getClassLoader());
            if (!ClassUtils.isAssignable(declaredClass, refClass, true)) {
                throw new ClassCastException("Can't assign " + declaredClass + " to reference of type " + refClass);
            }
            return new Sealed<>(handle);
        } catch (Exception e) {
            ParceledThrowable.throwUnchecked(e);
            return null;
        }
    }

    private Sealed() {
        handle = null;
        debug = null;
    }

    /*package*/ Sealed(IHandle handle) {
        setHandle(handle);
    }

    public Sealed(Sealed<? extends T> other) {
        assignFrom(other);
    }
	
	public synchronized IHandle getHandle() {
		return handle;
	}

    public <U> Sealed<U> castUnchecked() {
        return new Sealed<U>(this.handle);
    }

	/*internal*/ synchronized void setHandle(IHandle handle) {
		this.handle = handle;
        this.debug = null;
	}

    public void assignFrom(Sealed<? extends T> other) {
        IHandle handle;
        IHandleDebug debug;
        synchronized (other) {
            handle = other.handle;
            debug = other.debug;
        }
        synchronized (this) {
            this.handle = handle;
            this.debug = debug;
        }
    }

    public <TNextBuilder extends CallBuilder,
            TArgBuilder extends QuarentineModule.SArgBase<?, ? super T, TNextBuilder>>
    TNextBuilder buildCall(TArgBuilder builder) {
        return builder.arg(this);
    }

    //region Debugging
    public synchronized IHandleDebug getDebug() {
        if (debug == null) {
            // Try initializing.
            if (handle == null) {
                debug = new NullDebug();
            } else {
                try {
                    debug = handle.getDebug();
                } catch (RemoteException e) {
                    throw new WrappedRemoteException(e);
                }
                if (debug == null) {
                    throw new UnsupportedOperationException("Debugging not allowed for this app");
                }
            }
        }
        return debug;
    }

    @SuppressWarnings("unchecked")
    private T declassifyCommon(ParceledPayloadExceptionResult result, ClassLoader loader) {
        return (T)result.getResult().getValue(loader);
    }

    public T debugGetData() {
        return debugGetData(getClass().getClassLoader());
    }

	public T debugGetData(ClassLoader loader) {
        try {
            return declassifyCommon(getDebug().getData(), loader);
        } catch (RemoteException e) {
            ParceledThrowable.throwUnchecked(e);
            return null;
        }
    }

    public T declassify() {
        return declassify(true);
    }

    public T declassify(boolean mergeTaints) {
        return declassify(mergeTaints, getClass().getClassLoader());
    }

    public T declassify(boolean mergeTaints, ClassLoader loader) {
        try {
            ParceledPayloadExceptionResult result = getHandle().tryDeclassify(mergeTaints);
            if (result == null) {
                throw new SecurityException("Declassify denied");
            }
            return declassifyCommon(result, loader);
        } catch (RemoteException e) {
            ParceledThrowable.throwUnchecked(e);
            return null;
        }
    }

	public boolean debugIsComplete() {
        try {
            IHandleDebug debug = getDebug();
            return debug.isComplete();
        } catch (RemoteException e) {
            ParceledThrowable.throwUnchecked(e);
            return false;
        }
    }

    public void debugWaitForComplete() {
        try {
            IHandleDebug debug = getDebug();
            debug.waitForComplete();
        } catch (RemoteException e) {
            ParceledThrowable.throwUnchecked(e);
        }
    }

	public TaintSet debugGetTaints() {
        try {
            IHandleDebug debug = getDebug();
            return debug.getTaints();
        } catch (RemoteException e) {
            ParceledThrowable.throwUnchecked(e);
            return null;
        }
    }

    public String debugGetClassName() {
        try {
            IHandleDebug debug = getDebug();
            return debug.getClassName();
        } catch (RemoteException e) {
            ParceledThrowable.throwUnchecked(e);
            return null;
        }
    }

    public Class<?> debugGetClass() throws ClassNotFoundException {
        return debugGetClass(getClass().getClassLoader());
    }

    public Class<?> debugGetClass(ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(debugGetClassName(), true, loader);
    }

    private static final class NullDebug extends Binder implements IHandleDebug {
        private static ParceledPayloadExceptionResult result = null;
        private static synchronized ParceledPayloadExceptionResult getResult() {
            if (result == null) {
                result = new ParceledPayloadExceptionResult();
                result.setResult(ParceledPayload.create(null));
            }
            return result;
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public void waitForComplete() {
        }

        @Override
        public ParceledPayloadExceptionResult getData() {
            return getResult();
        }

        @Override
        public String getClassName() {
            throw new NullPointerException();
        }

        @Override
        public TaintSet getTaints() {
            return TaintSet.EMPTY;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
    //endregion
}

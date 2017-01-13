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

package edu.umich.oasis.sandbox;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import edu.umich.oasis.common.HandleDestroyedException;
import edu.umich.oasis.common.ParceledPayload;
import edu.umich.oasis.internal.IResolvedSoda;
import edu.umich.oasis.internal.ISandboxObject;
import edu.umich.oasis.common.ParceledPayloadExceptionResult;

/*package*/ class SandboxObject extends ISandboxObject.Stub {
    private static final String TAG = "OASIS.Handle";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private Object mObject;
    private ResolvedSoda mCreator;

    private SandboxObject(ResolvedSoda creator, Object obj) {
        mCreator = creator;
        mObject = obj;
    }

    @Override
    public synchronized String getDeclaredClassName() {
        if (mCreator != null) {
            return mCreator.getResultType();
        } else {
            return null;
        }
    }

    @Override
    public synchronized String getActualClassName() {
        if (mObject != null) {
            return mObject.getClass().getName();
        } else {
            return null;
        }
    }

    @Override
    public synchronized IResolvedSoda getCreator() {
        return mCreator;
    }

    @Override
    public synchronized ParceledPayloadExceptionResult marshalOut() {
        ParceledPayloadExceptionResult result = new ParceledPayloadExceptionResult();
        try {
            if (!isDestroyed()) {
                result.setResult(ParceledPayload.create(mObject));
            } else {
                result.setException(new HandleDestroyedException("SandboxObject destroyed"));
            }
        } catch (Exception e) {
            result.setException(e);
        }
        return result;
    }

    @Override
    public synchronized void destroy() {
        mCreator = null;
        mObject = null;
    }

    @Override
    public synchronized boolean isDestroyed() {
        return (mCreator == null);
    }

    public static IBinder binderForObject(ResolvedSoda creator, Object obj) {
        if (creator == null) {
            throw new NullPointerException();
        } else if (obj == null) {
            return null;
        } else {
            return new SandboxObject(creator, obj);
        }
    }

    public static Object objectForBinder(IBinder binder, ClassLoader loader, boolean release) throws RemoteException {
        if (binder == null) {
            return null;
        } else if (binder instanceof SandboxObject) {
            // Object is local; look it up directly.
            SandboxObject sbo = (SandboxObject)binder;
            if (sbo.isDestroyed()) {
                throw new HandleDestroyedException();
            }
            Object obj = sbo.mObject;
            if (release) {
                sbo.destroy();
            }
            return obj;
        } else {
            // Remote - go through Parcel.
            return marshalBinder(binder).getValue(loader);
        }
    }

    public static ParceledPayload marshalBinder(IBinder binder) throws RemoteException {
        if (binder == null) {
            return null;
        } else {
            ISandboxObject iface = ISandboxObject.Stub.asInterface(binder);
            return iface.marshalOut().getResult();
        }
    }

    @Override
    public String toString() {
        return String.format("SandboxObject[%s]", mObject);
    }
}

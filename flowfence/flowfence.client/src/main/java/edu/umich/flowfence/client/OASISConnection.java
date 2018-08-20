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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFormatException;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import org.apache.commons.lang3.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.umich.flowfence.common.IOASISService;
import edu.umich.flowfence.common.ISoda;
import edu.umich.flowfence.common.OASISConstants;
import edu.umich.flowfence.common.ParceledPayload;
import edu.umich.flowfence.common.SodaDescriptor;
import edu.umich.flowfence.common.SodaDetails;
import edu.umich.flowfence.common.SodaExceptionResult;


public final class OASISConnection implements AutoCloseable {
    private static final String TAG = "OASIS.Client";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    public interface Callback {
        void onConnect(OASISConnection conn) throws Exception;
    }

    public interface DisconnectCallback extends Callback {
        void onDisconnect(OASISConnection conn) throws Exception;
    }

    public static ServiceConnection bind(final Context context, final Callback callback) {
        final ServiceConnection connection = new ServiceConnection() {
            private OASISConnection conn;
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IOASISService service = IOASISService.Stub.asInterface(iBinder);
                conn = new OASISConnection(context, this, service);
                try {
                    callback.onConnect(conn);
                } catch (Exception e) {
                    Log.e(TAG, "Unhandled exception in onConnect", e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.e(TAG, "Lost Binder connection to OASIS");
                if (callback instanceof DisconnectCallback) {
                    try {
                        ((DisconnectCallback)callback).onDisconnect(conn);
                    } catch (Exception e) {
                        Log.e(TAG, "Unhandled exception in onDisconnect", e);
                    }
                }
                conn.closeInternal();
            }
        };

        Intent serviceIntent = new Intent();
        serviceIntent.setComponent(OASISFramework.getServiceComponent(context));
        if (context.bindService(serviceIntent, connection,
                Context.BIND_AUTO_CREATE |
                Context.BIND_ADJUST_WITH_ACTIVITY |
                Context.BIND_ABOVE_CLIENT |
                Context.BIND_IMPORTANT)) {
            return connection;
        } else {
            return null;
        }
    }

    private Context mContext;
    private ServiceConnection mConnection;
    private IOASISService mService;

    private OASISConnection(Context context, ServiceConnection connection, IOASISService service) {
        mContext = context;
        mConnection = connection;
        mService = service;
    }

    public void close() {
        if (mConnection != null && mContext != null) {
            mContext.unbindService(mConnection);
        }
        closeInternal();
    }

    private void closeInternal() {
        mConnection = null;
        mContext = null;
        mService = null;
    }

    private ISoda svcResolve(SodaDescriptor desc, SodaDetails details) throws RemoteException {
        if (localLOGD) {
            Log.d(TAG, String.format(">>> Resolving %s", desc));
        }

        SodaExceptionResult result = mService.resolveSODA(desc, 0, details);

        if (localLOGD) {
            Log.d(TAG, String.format("<<< Result: %s", result));
        }

        return result.getResult();
    }

    private ISoda svcResolveInstance(SodaDetails details, Class<?> clazz, String methodName, Class<?>... paramClasses) throws RemoteException {
        SodaDescriptor desc = SodaDescriptor.forInstance(mContext, clazz, methodName, paramClasses);
        return svcResolve(desc, details);
    }

    private ISoda svcResolveStatic(SodaDetails details, Class<?> clazz, String methodName, Class<?>... paramClasses) throws RemoteException {
        SodaDescriptor desc = SodaDescriptor.forStatic(mContext, clazz, methodName, paramClasses);
        return svcResolve(desc, details);
    }

    private ISoda svcResolveConstructor(SodaDetails details, Class<?> clazz, Class<?>... paramClasses) throws RemoteException {
        SodaDescriptor desc = SodaDescriptor.forConstructor(mContext, clazz, paramClasses);
        return svcResolve(desc, details);
    }

    public IOASISService getRawInterface() {
        return mService;
    }

    public <TThis, TResult>
    Soda.S1<TThis, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName);
        return new Soda.S1<>(soda, details, resultType);
    }

    public <TResult>
    Soda.S0<TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName);
        return new Soda.S0<>(soda, details, resultType);
    }

    public <TResult>
    Soda.S0<TResult> resolveConstructor(
            Class<TResult> resultType)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType);
        return new Soda.S0<>(soda, details, resultType);
    }

    public <TThis, T1, TResult>
    Soda.S2<TThis, T1, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1);
        return new Soda.S2<>(soda, details, resultType);
    }

    public <T1, TResult>
    Soda.S1<T1, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1);
        return new Soda.S1<>(soda, details, resultType);
    }

    public <T1, TResult>
    Soda.S1<T1, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1);
        return new Soda.S1<>(soda, details, resultType);
    }
    public <TThis, T1, T2, TResult>
    Soda.S3<TThis, T1, T2, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1, t2);
        return new Soda.S3<>(soda, details, resultType);
    }

    public <T1, T2, TResult>
    Soda.S2<T1, T2, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2);
        return new Soda.S2<>(soda, details, resultType);
    }

    public <T1, T2, TResult>
    Soda.S2<T1, T2, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2);
        return new Soda.S2<>(soda, details, resultType);
    }

    public <TThis, T1, T2, T3, TResult>
    Soda.S4<TThis, T1, T2, T3, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1, t2, t3);
        return new Soda.S4<>(soda, details, resultType);
    }

    public <T1, T2, T3, TResult>
    Soda.S3<T1, T2, T3, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2, t3);
        return new Soda.S3<>(soda, details, resultType);
    }

    public <T1, T2, T3, TResult>
    Soda.S3<T1, T2, T3, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2, t3);
        return new Soda.S3<>(soda, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, TResult>
    Soda.S5<TThis, T1, T2, T3, T4, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4);
        return new Soda.S5<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, TResult>
    Soda.S4<T1, T2, T3, T4, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4);
        return new Soda.S4<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, TResult>
    Soda.S4<T1, T2, T3, T4, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2, t3, t4);
        return new Soda.S4<>(soda, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, T5, TResult>
    Soda.S6<TThis, T1, T2, T3, T4, T5, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4, t5);
        return new Soda.S6<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, TResult>
    Soda.S5<T1, T2, T3, T4, T5, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5);
        return new Soda.S5<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, TResult>
    Soda.S5<T1, T2, T3, T4, T5, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5);
        return new Soda.S5<>(soda, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, T5, T6, TResult>
    Soda.S7<TThis, T1, T2, T3, T4, T5, T6, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4, t5, t6);
        return new Soda.S7<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, TResult>
    Soda.S6<T1, T2, T3, T4, T5, T6, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5, t6);
        return new Soda.S6<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, TResult>
    Soda.S6<T1, T2, T3, T4, T5, T6, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5, t6);
        return new Soda.S6<>(soda, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, T5, T6, T7, TResult>
    Soda.S8<TThis, T1, T2, T3, T4, T5, T6, T7, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4, t5, t6, t7);
        return new Soda.S8<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, TResult>
    Soda.S7<T1, T2, T3, T4, T5, T6, T7, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5, t6, t7);
        return new Soda.S7<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, TResult>
    Soda.S7<T1, T2, T3, T4, T5, T6, T7, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5, t6, t7);
        return new Soda.S7<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, TResult>
    Soda.S8<T1, T2, T3, T4, T5, T6, T7, T8, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7, Class<T8> t8)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5, t6, t7, t8);
        return new Soda.S8<>(soda, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, TResult>
    Soda.S8<T1, T2, T3, T4, T5, T6, T7, T8, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7, Class<T8> t8)
            throws RemoteException, ClassNotFoundException {
        SodaDetails details = new SodaDetails();
        ISoda soda = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5, t6, t7, t8);
        return new Soda.S8<>(soda, details, resultType);
    }
}

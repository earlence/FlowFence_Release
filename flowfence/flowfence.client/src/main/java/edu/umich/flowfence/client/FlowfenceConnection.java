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
import android.os.RemoteException;
import android.util.Log;

import edu.umich.flowfence.common.IFlowfenceService;
import edu.umich.flowfence.common.IQM;
import edu.umich.flowfence.common.QMDescriptor;
import edu.umich.flowfence.common.QMDetails;
import edu.umich.flowfence.common.QMExceptionResult;


public final class FlowfenceConnection implements AutoCloseable {
    private static final String TAG = "FF.Client";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    public interface Callback {
        void onConnect(FlowfenceConnection conn) throws Exception;
    }

    public interface DisconnectCallback extends Callback {
        void onDisconnect(FlowfenceConnection conn) throws Exception;
    }

    public static ServiceConnection bind(final Context context, final Callback callback) {
        final ServiceConnection connection = new ServiceConnection() {
            private FlowfenceConnection conn;
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IFlowfenceService service = IFlowfenceService.Stub.asInterface(iBinder);
                conn = new FlowfenceConnection(context, this, service);
                try {
                    callback.onConnect(conn);
                } catch (Exception e) {
                    Log.e(TAG, "Unhandled exception in onConnect", e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.e(TAG, "Lost Binder connection to FlowFence");
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
        serviceIntent.setComponent(FlowfenceFramework.getServiceComponent(context));
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
    private IFlowfenceService mService;

    private FlowfenceConnection(Context context, ServiceConnection connection, IFlowfenceService service) {
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

    private IQM svcResolve(QMDescriptor desc, QMDetails details) throws RemoteException {
        if (localLOGD) {
            Log.d(TAG, String.format(">>> Resolving %s", desc));
        }

        QMExceptionResult result = mService.resolveQM(desc, 0, details);

        if (localLOGD) {
            Log.d(TAG, String.format("<<< Result: %s", result));
        }

        return result.getResult();
    }

    private IQM svcResolveInstance(QMDetails details, Class<?> clazz, String methodName, Class<?>... paramClasses) throws RemoteException {
        QMDescriptor desc = QMDescriptor.forInstance(mContext, clazz, methodName, paramClasses);
        return svcResolve(desc, details);
    }

    private IQM svcResolveStatic(QMDetails details, Class<?> clazz, String methodName, Class<?>... paramClasses) throws RemoteException {
        QMDescriptor desc = QMDescriptor.forStatic(mContext, clazz, methodName, paramClasses);
        return svcResolve(desc, details);
    }

    private IQM svcResolveConstructor(QMDetails details, Class<?> clazz, Class<?>... paramClasses) throws RemoteException {
        QMDescriptor desc = QMDescriptor.forConstructor(mContext, clazz, paramClasses);
        return svcResolve(desc, details);
    }

    public IFlowfenceService getRawInterface() {
        return mService;
    }

    public <TThis, TResult>
    QuarentineModule.S1<TThis, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName);
        return new QuarentineModule.S1<>(qm, details, resultType);
    }

    public <TResult>
    QuarentineModule.S0<TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName);
        return new QuarentineModule.S0<>(qm, details, resultType);
    }

    public <TResult>
    QuarentineModule.S0<TResult> resolveConstructor(
            Class<TResult> resultType)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType);
        return new QuarentineModule.S0<>(qm, details, resultType);
    }

    public <TThis, T1, TResult>
    QuarentineModule.S2<TThis, T1, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1);
        return new QuarentineModule.S2<>(qm, details, resultType);
    }

    public <T1, TResult>
    QuarentineModule.S1<T1, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1);
        return new QuarentineModule.S1<>(qm, details, resultType);
    }

    public <T1, TResult>
    QuarentineModule.S1<T1, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1);
        return new QuarentineModule.S1<>(qm, details, resultType);
    }
    public <TThis, T1, T2, TResult>
    QuarentineModule.S3<TThis, T1, T2, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1, t2);
        return new QuarentineModule.S3<>(qm, details, resultType);
    }

    public <T1, T2, TResult>
    QuarentineModule.S2<T1, T2, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2);
        return new QuarentineModule.S2<>(qm, details, resultType);
    }

    public <T1, T2, TResult>
    QuarentineModule.S2<T1, T2, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2);
        return new QuarentineModule.S2<>(qm, details, resultType);
    }

    public <TThis, T1, T2, T3, TResult>
    QuarentineModule.S4<TThis, T1, T2, T3, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1, t2, t3);
        return new QuarentineModule.S4<>(qm, details, resultType);
    }

    public <T1, T2, T3, TResult>
    QuarentineModule.S3<T1, T2, T3, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2, t3);
        return new QuarentineModule.S3<>(qm, details, resultType);
    }

    public <T1, T2, T3, TResult>
    QuarentineModule.S3<T1, T2, T3, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2, t3);
        return new QuarentineModule.S3<>(qm, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, TResult>
    QuarentineModule.S5<TThis, T1, T2, T3, T4, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4);
        return new QuarentineModule.S5<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, TResult>
    QuarentineModule.S4<T1, T2, T3, T4, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4);
        return new QuarentineModule.S4<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, TResult>
    QuarentineModule.S4<T1, T2, T3, T4, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2, t3, t4);
        return new QuarentineModule.S4<>(qm, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, T5, TResult>
    QuarentineModule.S6<TThis, T1, T2, T3, T4, T5, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4, t5);
        return new QuarentineModule.S6<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, TResult>
    QuarentineModule.S5<T1, T2, T3, T4, T5, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5);
        return new QuarentineModule.S5<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, TResult>
    QuarentineModule.S5<T1, T2, T3, T4, T5, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5);
        return new QuarentineModule.S5<>(qm, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, T5, T6, TResult>
    QuarentineModule.S7<TThis, T1, T2, T3, T4, T5, T6, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4, t5, t6);
        return new QuarentineModule.S7<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, TResult>
    QuarentineModule.S6<T1, T2, T3, T4, T5, T6, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5, t6);
        return new QuarentineModule.S6<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, TResult>
    QuarentineModule.S6<T1, T2, T3, T4, T5, T6, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5, t6);
        return new QuarentineModule.S6<>(qm, details, resultType);
    }

    public <TThis, T1, T2, T3, T4, T5, T6, T7, TResult>
    QuarentineModule.S8<TThis, T1, T2, T3, T4, T5, T6, T7, TResult> resolveInstance(
            Class<TResult> resultType, Class<TThis> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveInstance(details, clazz, methodName, t1, t2, t3, t4, t5, t6, t7);
        return new QuarentineModule.S8<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, TResult>
    QuarentineModule.S7<T1, T2, T3, T4, T5, T6, T7, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5, t6, t7);
        return new QuarentineModule.S7<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, TResult>
    QuarentineModule.S7<T1, T2, T3, T4, T5, T6, T7, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5, t6, t7);
        return new QuarentineModule.S7<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, TResult>
    QuarentineModule.S8<T1, T2, T3, T4, T5, T6, T7, T8, TResult> resolveStatic(
            Class<TResult> resultType, Class<?> clazz, String methodName,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7, Class<T8> t8)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveStatic(details, clazz, methodName, t1, t2, t3, t4, t5, t6, t7, t8);
        return new QuarentineModule.S8<>(qm, details, resultType);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, TResult>
    QuarentineModule.S8<T1, T2, T3, T4, T5, T6, T7, T8, TResult> resolveConstructor(
            Class<TResult> resultType,
            Class<T1> t1, Class<T2> t2, Class<T3> t3, Class<T4> t4,
            Class<T5> t5, Class<T6> t6, Class<T7> t7, Class<T8> t8)
            throws RemoteException, ClassNotFoundException {
        QMDetails details = new QMDetails();
        IQM qm = svcResolveConstructor(details, resultType, t1, t2, t3, t4, t5, t6, t7, t8);
        return new QuarentineModule.S8<>(qm, details, resultType);
    }
}

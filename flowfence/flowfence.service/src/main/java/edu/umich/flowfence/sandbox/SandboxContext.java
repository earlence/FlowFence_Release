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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import edu.umich.flowfence.common.IEventChannelAPI;
import edu.umich.flowfence.common.IFlowfenceService;
import edu.umich.flowfence.common.INetworkAPI;
import edu.umich.flowfence.common.ISensitiveViewAPI;
import edu.umich.flowfence.common.ITaintAPI;
import edu.umich.flowfence.common.IDynamicAPI;
import edu.umich.flowfence.common.FlowfenceContext;
import edu.umich.flowfence.common.ParceledPayload;
import edu.umich.flowfence.common.RemoteCallException;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.common.smartthings.ISmartSwitchAPI;
import edu.umich.flowfence.common.smartthings.SmartDevice;
import edu.umich.flowfence.events.IEventChannelSender;
import edu.umich.flowfence.internal.ITrustedAPI;
import edu.umich.flowfence.kvs.IRemoteSharedPrefs;
import edu.umich.flowfence.service.BuildConfig;
import edu.umich.flowfence.service.FlowfenceService;

/*package*/ final class SandboxContext extends FlowfenceContext {
    private static final String TAG = "FF.Context";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private final ITrustedAPI mCallout;
    private final IFlowfenceService mRootService;
    private final ClassLoader mLoader;
    private final String mPackageName;
    private final Context mBaseContext;

    private class APIBase implements IDynamicAPI {
        @Override
        public Object invoke(String method, Object... args) {
            if (localLOGV) {
                Log.v(TAG, "Invoking " + method);
            }
            try {
                return MethodUtils.invokeMethod(this, method, args);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target instanceof RuntimeException) {
                    throw (RuntimeException)target;
                } else {
                    throw new RuntimeException(target);
                }
            }
        }
    }

    /*package*/ SandboxContext(Context base, String packageName, ClassLoader loader,
                               ITrustedAPI callout, IFlowfenceService rootService) {
        super(base);
        this.mBaseContext = base;
        this.mPackageName = packageName;
        this.mLoader = loader;
        this.mCallout = Objects.requireNonNull(callout);
        this.mRootService = Objects.requireNonNull(rootService);
    }

    public synchronized void beginQM() {
        FlowfenceContext.setInstance(this);
    }

    public synchronized void endQM() {
        FlowfenceContext.setInstance(null);
    }

    private void checkNotFinished() {
        if (!FlowfenceContext.isInQM()) {
            throw new IllegalStateException("Cannot use context after QM complete");
        }
    }

    private RuntimeException translateException(Exception e) {
        RuntimeException toThrow = null;

        if (e instanceof RemoteException) {
            toThrow = new RemoteCallException(e);
        } else if (e instanceof RuntimeException) {
            toThrow = (RuntimeException)e;
        } else {
            toThrow = new RuntimeException(e);
        }

        Log.w(TAG, "Exception in sandbox callout", toThrow);
        return toThrow;
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return new SandboxContext(mBaseContext, packageName, null, mCallout, mRootService);
    }

    @Override
    public ClassLoader getClassLoader() {
        return mLoader;
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    private static final Map<String, Map<String, RemoteSharedPrefsWrapper>> g_mPrefsMap = new HashMap<>();
    private Map<String, RemoteSharedPrefsWrapper> mPackagePrefsMap = null;

    @Override
    public RemoteSharedPrefsWrapper getSharedPreferences(String name, int mode) {
        checkNotFinished();

        if (mPackagePrefsMap == null) {
            synchronized (g_mPrefsMap) {
                mPackagePrefsMap = g_mPrefsMap.get(getPackageName());
                if (mPackagePrefsMap == null) {
                    mPackagePrefsMap = new HashMap<>();
                    g_mPrefsMap.put(getPackageName(), mPackagePrefsMap);
                }
            }
        }

        RemoteSharedPrefsWrapper prefs = null;
        synchronized (mPackagePrefsMap) {
            prefs = mPackagePrefsMap.get(name);
            if (prefs == null) {
                try {
                    IRemoteSharedPrefs remotePrefs = mCallout.openSharedPrefs(getPackageName(), name, mode);
                    prefs = new RemoteSharedPrefsWrapper(remotePrefs);
                } catch (Exception e) {
                    throw translateException(e);
                }

                mPackagePrefsMap.put(name, prefs);
            }
        }

        return prefs;
    }

    private final WeakHashMap<ServiceConnection, ComponentName> mBoundServices = new WeakHashMap<>();

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        ComponentName component = service.getComponent();
        if (component != null &&
                component.getPackageName().equals(BuildConfig.APPLICATION_ID) &&
                component.getClassName().equals(FlowfenceService.class.getName())) {
            IBinder rootBinder = mRootService.asBinder();
            ComponentName oldMapping;
            synchronized (mBoundServices) {
                oldMapping = mBoundServices.put(conn, component);
            }
            if (oldMapping == null) {
                conn.onServiceConnected(component, rootBinder);
            }
            return true;
        }
        return super.bindService(service, conn, flags);
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        synchronized (mBoundServices) {
            ComponentName removed = mBoundServices.remove(conn);
            if (removed != null) {
                conn.onServiceDisconnected(removed);
                return;
            }
        }
        super.unbindService(conn);
    }

    // FIXME: make this prettier

    public class ToastAPI extends APIBase {
        public void showText(CharSequence text, int duration) {
            try {
                mCallout.showToast(text, duration);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    public class PushAPI extends APIBase {
        public void sendPush(String title, String body) {
            try {
                mCallout.sendPush(title, body);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    public class TaintAPI extends APIBase implements ITaintAPI {
        @Override
        public void addTaint(TaintSet ts) {
            try {
                mCallout.taintSelf(ts);
            } catch (Exception e) {
                throw translateException(e);
            }
        }

        @Override
        public TaintSet removeTaint(TaintSet toRemove) {
            try {
                return mCallout.removeTaints(toRemove);
            } catch (Exception e) {
                throw translateException(e);
            }
        }

        @Override
        public TaintSet removeTaint(Set<String> toRemove) {
            TaintSet.Builder builder = new TaintSet.Builder();
            for (String name : toRemove) {
                builder.addTaint(new ComponentName(getPackageName(), name));
            }
            return removeTaint(builder.build());
        }
    }

    public class SmartSwitchAPI extends APIBase implements ISmartSwitchAPI {
        @SuppressWarnings("unchecked")
        public List<SmartDevice> getSwitches() {

            List<SmartDevice> switches = null;
            try {
                return mCallout.getSwitches();
            } catch(Exception e) {
                throw translateException(e);
            }
        }

        public void switchOp(String op, String switchId) {
            try {
                mCallout.switchOp(op, switchId);
            } catch(Exception e) {
                throw translateException(e);
            }
        }
    }

    public class EventChannelAPI extends APIBase implements IEventChannelAPI {
        @Override
        public void fireEvent(ComponentName channelName, Object... args) {
            fireEvent(null, channelName, args);
        }

        @Override
        public void fireEvent(TaintSet extraTaint, ComponentName channelName, Object... args) {
            try {
                IEventChannelSender sender = mCallout.getEventChannel(channelName);
                List<ParceledPayload> payloadList = new ArrayList<>(args.length);
                for (Object arg : args) {
                    payloadList.add(ParceledPayload.create(arg));
                }
                sender.fire(payloadList, extraTaint);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    public class SensitiveViewAPI extends APIBase implements ISensitiveViewAPI {
        @Override
        public void addSensitiveValue(String viewId, String value) {
            try {
                mCallout.addSensitiveValue(viewId, value);
            } catch(Exception e) {
                throw translateException(e);
            }
        }

        @Override
        public String readSensitiveValue(String viewId, TaintSet taint) {
            try {
                return mCallout.readSensitiveValue(viewId, taint);
            } catch(Exception e) {
                throw translateException(e);
            }
        }
    }

    public class NetworkAPI extends APIBase implements INetworkAPI {

        @Override
        public String get(String url) {
            return getWithQuery(url, null);
        }

        @Override
        public String getWithQuery(String url, Map query) {
            try{
                return mCallout.getWithQuery(url, query);
            }
            catch (Exception ex){
               throw translateException(ex);
            }
        }

        @Override
        public String post(String url, Map body) {
            try{
                return mCallout.post(url, body);
            }
            catch (Exception ex){
               throw translateException(ex);
            }
        }
    }

    @Override
    public Object getTrustedAPI(String apiName) {
        if (localLOGD) {
            Log.d(TAG, "Looking up API " + apiName);
        }
        switch (apiName) {
            case "toast":
                return new ToastAPI();
            case "push":
                return new PushAPI();
            case "taint":
                return new TaintAPI();
            case "smartswitch":
                return new SmartSwitchAPI();
            case "event":
                return new EventChannelAPI();
            case "ui":
                return new SensitiveViewAPI();
            case "network":
                return new NetworkAPI();
            default:
                return null;
        }
    }
}
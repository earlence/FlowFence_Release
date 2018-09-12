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

package edu.umich.flowfence.service;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Debug;
import android.os.FileUtils;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;


import com.androidnetworking.AndroidNetworking;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import edu.umich.flowfence.common.ExceptionResult;
import edu.umich.flowfence.common.FlowfenceConstants;
import edu.umich.flowfence.common.IFlowfenceService;
import edu.umich.flowfence.common.IQM;
import edu.umich.flowfence.common.QMDescriptor;
import edu.umich.flowfence.common.QMDetails;
import edu.umich.flowfence.common.QMExceptionResult;
import edu.umich.flowfence.common.ResolveFlags;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.policy.PackageManifest;
import edu.umich.flowfence.policy.PolicyParseException;
import okhttp3.OkHttpClient;

public class FlowfenceApplication extends ContextWrapper {
    private static final String TAG = "FF.Application";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    public static class Impl extends Application {
        @Override
        public void onCreate() {
            super.onCreate();
            if (!android.os.Process.isIsolated()) {
                new FlowfenceApplication(this).onCreate();
            }
        }
    }

    private static FlowfenceApplication instance;

    public static FlowfenceApplication getInstance() {
        return instance;
    }

    private final Object mSync = new Object();
    private final HashMap<String, PackageManifest> mManifestMap = new HashMap<>();
    private final HashMap<QMDescriptor, QMRef> mResolvedMap = new HashMap<>();
    private final SandboxManager mSandboxManager;

    private final Handler mUIHandler = new Handler(getMainLooper());
    private final ExecutorService mBackgroundPool = Executors.newCachedThreadPool();

    private FlowfenceService mService;

    private FlowfenceApplication(Context base)
    {
        super(base);
        instance = this;
        mSandboxManager = new SandboxManager();
        FileUtils.setPermissions(getDir("shared_prefs", 0), FileUtils.S_IRWXU|FileUtils.S_IRWXG, -1, -1);
        Log.i(TAG, "created");
    }

    private void onCreate()
    {
        initializeNetwork();
    }

    private void initializeNetwork()
    {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        AndroidNetworking.initialize(getApplicationContext(), client);
    }

    /* package */ void onServiceCreate(FlowfenceService service)
    {
        mService = service;
        mSandboxManager.start();
    }

    /* package */ void onServiceDestroy()
    {
        mSandboxManager.stop();
        mService = null;
    }

    /* package */ FlowfenceService getService() {
        return mService;
    }

    private static Pattern PACKAGE_NAME_PATTERN = Pattern.compile("^" + FlowfenceConstants.JAVA_PACKAGE_PATTERN + "$");

    public String checkPackageName(String packageName) {
        if (!PACKAGE_NAME_PATTERN.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid package name "+packageName);
        }
        return packageName;
    }

    public IFlowfenceService.Stub getBinder()
    {
        return mBinder;
    }

    /* package */ QMRef resolveQM(QMDescriptor descriptor, int flags) throws Exception {
        return resolveQM(descriptor, flags, null);
    }

    /* package */ QMRef resolveQM(QMDescriptor descriptor, int flags, QMDetails details)
            throws Exception {
        synchronized (mSync) {
            QMRef ref = mResolvedMap.get(descriptor);
            if (ref == null) {
                boolean bestMatch = (flags & ResolveFlags.BEST_MATCH) != 0;
                ref = new QMRef(descriptor, bestMatch, details);
                mResolvedMap.put(descriptor, ref);
            } else if ((flags & ResolveFlags.FORCE_RESOLVE) != 0) {
                Sandbox sb = getSandboxForResolve(descriptor.definingClass.getPackageName());
                try {
                    ref.forget(sb);
                    ref.resolveFor(sb);
                } finally {
                    putSandbox(sb);
                }
            }
            return ref;
        }
    }

    //this is the public interface of FlowFence to client apps
    private final IFlowfenceService.Stub mBinder = new IFlowfenceService.Stub()
    {
        //call into the sandbox and call a method on a created QM
        @Override
        public QMExceptionResult resolveQM(QMDescriptor descriptor, int flags, QMDetails details)
        {
            if (localLOGD) {
                String callingPackage = getPackageManager().getNameForUid(Binder.getCallingUid());
                Log.d(TAG, String.format("resolveQM [%s] from package %s", descriptor, callingPackage));
            }
            QMExceptionResult xr = new QMExceptionResult();
            try {
                xr.setResult(FlowfenceApplication.this.resolveQM(descriptor, flags, details));
            } catch (Throwable t) {
                Log.e(TAG, "Failed to resolve", t);
                xr.setException(t);
            }
            return xr;
        }

        @Override
        public int setSandboxCount(int count) {
            return mSandboxManager.setMaxSandboxCount(count);
        }

        @Override
        public int setMaxIdleCount(int count) {
            return mSandboxManager.setMaxIdleSandboxCount(count);
        }

        @Override
        public int setMinHotSpare(int count) {
            return mSandboxManager.setMinHotSpare(count);
        }

        @Override
        public int setMaxHotSpare(int count) {
            return mSandboxManager.setMaxHotSpare(count);
        }


        @Override
        public synchronized void restartSandbox(int sandboxId) {
            Sandbox sb = mSandboxManager.getSandboxById(sandboxId, null);
            try {
                sb.restart();
            } finally {
                mSandboxManager.putSandbox(sb);
            }
        }

        @Override
        public void forceGarbageCollection() throws RemoteException {
            System.gc();
            for (int i = 0; i < FlowfenceConstants.NUM_SANDBOXES; i++) {
                Sandbox.get(i).gc();
            }
            System.gc();
        }

        @Override
        public Debug.MemoryInfo dumpMemoryInfo(List<Debug.MemoryInfo> sandboxInfo) throws RemoteException {
            sandboxInfo.clear();
            for (int i = 0; i < FlowfenceConstants.NUM_SANDBOXES; i++) {
                sandboxInfo.add(Sandbox.get(i).getMemoryInfo());
            }

            Debug.MemoryInfo rv = new Debug.MemoryInfo();
            Debug.getMemoryInfo(rv);
            return rv;
        }

        private final ExceptionResult<Boolean> TRUE_RESULT = new ExceptionResult<>(Boolean.TRUE);
        private final ExceptionResult<Boolean> FALSE_RESULT = new ExceptionResult<>(Boolean.FALSE);
        private ExceptionResult<Boolean> resultFor(boolean result) {
            return result ? TRUE_RESULT : FALSE_RESULT;
        }

        @Override
        public ExceptionResult<Boolean> subscribeEventChannel(ComponentName name, QMDescriptor desc) {
            try {
                QMRef ref = FlowfenceApplication.this.resolveQM(desc, 0);
                return resultFor(FlowfenceApplication.this.subscribeEventChannel(
                        name, desc, null, Sandbox.getCallingTaint()));
            } catch (Throwable t) {
                return new ExceptionResult<>(t);
            }
        }

        @Override
        public ExceptionResult<Boolean> unsubscribeEventChannel(ComponentName name, QMDescriptor desc) {
            try {
                return resultFor(FlowfenceApplication.this.unsubscribeEventChannel(
                        name, desc, null, Sandbox.getCallingTaint()));
            } catch (Throwable t) {
                return new ExceptionResult<>(t);
            }
        }

        @Override
        public ExceptionResult<Boolean> subscribeEventChannelHandle(ComponentName channel, IQM hRef) {
            try {
                QMRef ref = (hRef instanceof QMRef) ? (QMRef)hRef : null;
                QMDescriptor desc = hRef.getDescriptor();
                return resultFor(FlowfenceApplication.this.subscribeEventChannel(
                        channel, desc, ref, Sandbox.getCallingTaint()));
            } catch (Throwable t) {
                return new ExceptionResult<>(t);
            }
        }

        @Override
        public ExceptionResult<Boolean> unsubscribeEventChannelHandle(ComponentName channel, IQM hRef) {
            try {
                QMRef ref = (hRef instanceof QMRef) ? (QMRef)hRef : null;
                QMDescriptor desc = hRef.getDescriptor();
                return resultFor(FlowfenceApplication.this.unsubscribeEventChannel(
                        channel, desc, ref, Sandbox.getCallingTaint()));
            } catch (Throwable t) {
                return new ExceptionResult<>(t);
            }
        }
    };

    /* package */ Sandbox getSandboxForResolve(String packageName) {
        Sandbox sb = mSandboxManager.getSandboxForResolve(checkPackageName(packageName));
        sb.waitForStartupComplete();
        return sb;
    }

    /* package */ void getSandboxAsync(SandboxManager.AsyncCallback callback) {
        mSandboxManager.getSandboxAsync(callback);
    }

    /* package */ void putSandbox(Sandbox sb) {
        mSandboxManager.putSandbox(sb);
    }

    public Handler getUIHandler() {
        return mUIHandler;
    }

    public ExecutorService getBackgroundExecutor() {
        return mBackgroundPool;
    }

    public PackageManifest getManifestForPackage(String packageName) {
        PackageManifest manifest;
        checkPackageName(packageName);
        synchronized (mSync) {
            manifest = mManifestMap.get(packageName);
            if (manifest == null) {
                try {
                    Context packageContext = createPackageContext(packageName, 0);
                    manifest = new PackageManifest(packageContext);
                    mManifestMap.put(packageName, manifest);
                } catch (XmlPullParserException | IOException | PolicyParseException |
                        PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Failed to load manifest", e);
                }
            }
        }
        return manifest;
    }

    /* package */ void onPackageRemoved(String packageName) {
        mSandboxManager.onPackageRemoved(packageName);
        Sandbox.forgetKnownPackage(packageName);
        synchronized (mSync) {
            mManifestMap.remove(packageName);
            for (QMDescriptor descriptor : new HashSet<>(mResolvedMap.keySet())) {
                if (descriptor.definingClass.getPackageName().equals(packageName)) {
                    mResolvedMap.remove(descriptor);
                }
            }
        }
    }

    /* package */ EventChannel getChannel(ComponentName name) {
        PackageManifest manifest = getManifestForPackage(name.getPackageName());
        EventChannel channel = manifest.getChannels().get(name.getClassName());
        if (channel == null) {
            Log.e(TAG, "Can't find channel " + name.flattenToShortString());
            return null;
        }
        return channel;
    }

    /* package */ boolean subscribeEventChannel(ComponentName eventChannel, QMDescriptor desc, QMRef ref, TaintSet ts) throws Exception {
        EventChannel ch = getChannel(eventChannel);
        if (ch != null) {
            ch.subscribe(desc, ref, ts);
            return true;
        } else {
            return false;
        }
    }

    /* package */ boolean unsubscribeEventChannel(ComponentName eventChannel, QMDescriptor desc, QMRef ref, TaintSet ts) throws Exception {
        EventChannel ch = getChannel(eventChannel);
        if (ch != null) {
            ch.unsubscribe(desc, ref, ts);
            return true;
        } else {
            return false;
        }
    }
}

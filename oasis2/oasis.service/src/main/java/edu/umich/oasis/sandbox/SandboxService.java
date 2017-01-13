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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;

import dalvik.system.PathClassLoader;
import edu.umich.oasis.common.IOASISService;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.SodaDetails;
import edu.umich.oasis.internal.ISandboxService;
import edu.umich.oasis.internal.ITrustedAPI;
import edu.umich.oasis.internal.ResolvedSodaExceptionResult;

public abstract class SandboxService extends Service
{
    public static final String SERVICE_FORMAT = "edu.umich.oasis.sandbox.SandboxService$Impl%02X";
    public static final String EXTRA_TRUSTED_API = "edu.umich.oasis.service.ITrustedAPI";
    public static final String EXTRA_ROOT_SERVICE = "edu.umich.oasis.service.IOASISService";
    public static final String EXTRA_KNOWN_PACKAGES = "edu.umich.oasis.service.KnownPackages";
    public static final String EXTRA_SANDBOX_ID = "edu.umich.oasis.service.SandboxID";

    private static final String TAG = "OASIS.SandboxService";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    // region Sandbox process impls
    public static final class Impl00 extends SandboxService { }
    public static final class Impl01 extends SandboxService { }
    public static final class Impl02 extends SandboxService { }
    public static final class Impl03 extends SandboxService { }
    public static final class Impl04 extends SandboxService { }
    public static final class Impl05 extends SandboxService { }
    public static final class Impl06 extends SandboxService { }
    public static final class Impl07 extends SandboxService { }
    public static final class Impl08 extends SandboxService { }
    public static final class Impl09 extends SandboxService { }
    public static final class Impl0A extends SandboxService { }
    public static final class Impl0B extends SandboxService { }
    public static final class Impl0C extends SandboxService { }
    public static final class Impl0D extends SandboxService { }
    public static final class Impl0E extends SandboxService { }
    public static final class Impl0F extends SandboxService { }
    // endregion

    protected SandboxService()
    {
        mContextMap = new HashMap<>();
    }

    public static void resolveStub() {
        Log.i(TAG, "Resolve success");
    }

    private ITrustedAPI mTrustedAPI;
    private IOASISService mRootService;
    private int mID;

    @Override
    public IBinder onBind(Intent i)
    {
        if (localLOGD) {
            Log.d(TAG, "Bound");
        }
        Bundle extras = i.getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("No extras");
        }
        IBinder api = extras.getBinder(EXTRA_TRUSTED_API);
        if (api == null) {
            throw new IllegalArgumentException("Trusted API not found in extras");
        }
        IBinder root = extras.getBinder(EXTRA_ROOT_SERVICE);
        if (root == null) {
            throw new IllegalArgumentException("OASISService not found in extras");
        }
        mTrustedAPI = ITrustedAPI.Stub.asInterface(api);
        mRootService = IOASISService.Stub.asInterface(root);
        mID = extras.getInt(EXTRA_SANDBOX_ID, -1);

        if (localLOGV) {
            ClassLoader cl = getClassLoader();
            Log.v(TAG, "ClassLoader chain:");
            while (cl != null) {
                Log.v(TAG, cl.toString());
                cl = cl.getParent();
            }
            Log.v(TAG, "<end of chain>");
        }

        final String[] packagesToLoad = extras.getStringArray(EXTRA_KNOWN_PACKAGES);
        getBackgroundHandler().post(new Runnable() {
            @Override
            public void run() {
                if (localLOGD) {
                    Log.d(TAG, "Preloading resolve code");
                }

                // Run through a fake transaction, to preload the appropriate classes.
                SodaDescriptor preloadDesc = SodaDescriptor.forStatic(
                        SandboxService.this, SandboxService.class, "resolveStub");

                Binder testBinder = new Binder() {
                    @Override
                    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                        return mBinder.onTransact(code, data, reply, flags);
                    }
                };
                testBinder.attachInterface(null, "");

                ISandboxService proxy = ISandboxService.Stub.asInterface(testBinder);
                try {
                    proxy.resolveSoda(preloadDesc, false, null);
                } catch (Exception e) {
                    Log.w(TAG, "Couldn't preload resolve", e);
                }

                if (localLOGD) {
                    Log.d(TAG, "Preloading packages");
                }

                // Load up packages the trusted service tells us we might need.
                for (String packageName : ArrayUtils.nullToEmpty(packagesToLoad)) {
                    try {
                        if (localLOGD) {
                            Log.d(TAG, "Preloading "+packageName);
                        }
                        getContextForPackage(packageName);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "Can't preload package", e);
                    }
                }

                Log.i(TAG, "Sandbox #"+mID+": preload complete");
            }
        });
        return mBinder;
    }

    private final HandlerThread mWorkerThread = new HandlerThread("Sandbox worker");
    {
        mWorkerThread.setDaemon(true);
        mWorkerThread.start();
    }
    private Handler mBackgroundHandler;

    public Handler getBackgroundHandler() {
        synchronized (mWorkerThread) {
            if (mBackgroundHandler == null) {
                mBackgroundHandler = new Handler(mWorkerThread.getLooper());
            }
            return mBackgroundHandler;
        }
    }

    private HashMap<String, SandboxContext> mContextMap;

    private SandboxContext makePackageContext(String packageName) throws PackageManager.NameNotFoundException {
        // Load up context without code.
        Context pkgContext = createPackageContext(packageName, 0);
        ApplicationInfo ai = pkgContext.getApplicationInfo();
        // Crank up a ClassLoader - we can't use the default DexClassLoader in an isolated process.
        final ClassLoader loader = new PathClassLoader(
                ai.sourceDir,
                ai.nativeLibraryDir,
                getClassLoader()
        );

        return new SandboxContext(pkgContext, packageName, loader, mTrustedAPI, mRootService);
    }

    /*package*/ SandboxContext getContextForPackage(String packageName) throws PackageManager.NameNotFoundException {
        synchronized (mContextMap) {
            SandboxContext ctx = mContextMap.get(packageName);
            if (ctx == null) {
                ctx = makePackageContext(packageName);
                mContextMap.put(packageName, ctx);
            }
            return ctx;
        }
    }

    //this is the public interface of a sandbox to OASIS Service
    private final ISandboxService.Stub mBinder = new ISandboxService.Stub()
    {
        @Override
        public ResolvedSodaExceptionResult resolveSoda(SodaDescriptor desc, boolean bestMatch,
                                                       SodaDetails details) throws RemoteException {

            ResolvedSodaExceptionResult r = new ResolvedSodaExceptionResult();
            try {
                if (localLOGD) {
                    Log.d(TAG, "Sandbox #"+mID+": Resolving "+desc);
                }
                ResolvedSoda resolved = new ResolvedSoda(SandboxService.this, desc, bestMatch);
                resolved.getDetails(details);
                r.setResult(resolved);
            } catch (Exception e) {
                r.setException(e);
            }
            return r;
        }

        @Override
        public int getPid() {
            return android.os.Process.myPid();
        }

        @Override
        public int getUid() {
            return android.os.Process.myUid();
        }

        @Override
        public void kill() {
            int pid = android.os.Process.myPid();
            // Start with SIGTERM.
            android.os.Process.sendSignal(pid, 15);
            try {
                Thread.sleep(15 * 1000);
            } catch (InterruptedException e) {
                Log.w(TAG, e);
                return;
            }
            // If we're still around after 15 seconds, SIGKILL ourselves.
            android.os.Process.killProcess(pid);
        }

        @Override
        public Debug.MemoryInfo dumpMemoryInfo() {
            Debug.MemoryInfo rv = new Debug.MemoryInfo();
            Debug.getMemoryInfo(rv);
            return rv;
        }

        @Override
        public void gc() {
            System.gc();
        }
    };
}

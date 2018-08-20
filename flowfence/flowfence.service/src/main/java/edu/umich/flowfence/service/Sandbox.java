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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import org.apache.commons.lang3.Validate;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umich.flowfence.common.OASISConstants;
import edu.umich.flowfence.common.SodaDetails;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.internal.IResolvedSoda;
import edu.umich.flowfence.internal.ISandboxObject;
import edu.umich.flowfence.internal.ISandboxService;
import edu.umich.flowfence.internal.ResolvedSodaExceptionResult;
import edu.umich.flowfence.common.SodaDescriptor;
import edu.umich.flowfence.sandbox.SandboxService;

public final class Sandbox implements Comparable<Sandbox> {

    private static final String TAG = "OASIS.Sandbox";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    //region Events
    public static interface EventHandler {
        public boolean onEvent(String event, Sandbox sender, Object args) throws Exception;
    }

    public static final class EventChain {
        private final WeakHashMap<EventHandler, WeakReference<Object>> mHandlers = new WeakHashMap<>();
        private final EventChain mParent;
        private final String mEventName;

        public EventChain(String eventName) {
            mEventName = eventName;
            mParent = null;
        }

        public EventChain(EventChain parent) {
            mParent = parent;
            mEventName = parent.mEventName;
        }

        private boolean fireInternal(Sandbox sender, Object args, List<Exception> exceptions) {
            synchronized (mHandlers) {
                if (mParent != null) {
                    mParent.fireInternal(sender, args, exceptions);
                }
                Set<EventHandler> handlerSet = new HashSet<>(mHandlers.keySet());
                for (EventHandler handler : handlerSet) {
                    try {
                        if (handler.onEvent(mEventName, sender, args)) {
                            mHandlers.remove(handler);
                        }
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }
            }
            return !exceptions.isEmpty();
        }

        private void fire(Sandbox sender, Object args) {
            if (localLOGV) {
                Log.v(TAG, "Firing event chain " + mEventName);
            }
            ArrayList<Exception> exList = new ArrayList<>();
            if (fireInternal(sender, args, exList)) {
                RuntimeException e = new RuntimeException("Error firing event " + mEventName);
                for (Exception cause : exList) {
                    e.addSuppressed(cause);
                }
                throw e;
            }
        }

        public EventHandler register(Object token, EventHandler handler) {
            synchronized (mHandlers) {
                mHandlers.put(handler, new WeakReference<>(token));
                return handler;
            }
        }

        public void unregister(EventHandler handler) {
            synchronized (mHandlers) {
                mHandlers.remove(handler);
            }
        }

        public void unregisterAll(Object token) {
            synchronized (mHandlers) {
                mHandlers.values().removeAll(Collections.singleton(new WeakReference<>(token)));
            }
        }

        public String getName() {
            return mEventName;
        }
    }

    public static final EventChain g_onCreated = new EventChain("onCreated");
    public static final EventChain g_onBeforeConnect = new EventChain("onBeforeConnect");
    public static final EventChain g_onConnected = new EventChain("onConnected");
    public static final EventChain g_onBeforeDisconnect = new EventChain("onBeforeDisconnect");
    public static final EventChain g_onDisconnected = new EventChain("onDisconnected");
    public static final EventChain g_onBeforeTaintAdd = new EventChain("onBeforeTaintAdd");
    public static final EventChain g_onTaintAdded = new EventChain("onTaintAdded");
    public static final EventChain g_onBeforeTaintRemove = new EventChain("onBeforeTaintRemove");
    public static final EventChain g_onTaintRemoved = new EventChain("onTaintRemoved");
    public static final EventChain g_onExecutionStart = new EventChain("onExecutionStart");
    public static final EventChain g_onExecutionFinish = new EventChain("onExecutionFinish");

    public final EventChain onBeforeConnect = new EventChain(g_onBeforeConnect);
    public final EventChain onConnected = new EventChain(g_onConnected);
    public final EventChain onBeforeDisconnect = new EventChain(g_onBeforeDisconnect);
    public final EventChain onDisconnected = new EventChain(g_onDisconnected);
    public final EventChain onBeforeTaintAdd = new EventChain(g_onBeforeTaintAdd);
    public final EventChain onTaintAdded = new EventChain(g_onTaintAdded);
    public final EventChain onBeforeTaintRemove = new EventChain(g_onBeforeTaintRemove);
    public final EventChain onTaintRemoved = new EventChain(g_onTaintRemoved);
    public final EventChain onExecutionStart = new EventChain(g_onExecutionStart);
    public final EventChain onExecutionFinish = new EventChain(g_onExecutionFinish);
    //endregion

    //region PID-to-Sandbox mapping
    private static final ConcurrentMap<Integer, Sandbox> s_mPidMap = new ConcurrentHashMap<>();

    public static Sandbox forPid(int pid) {
        return s_mPidMap.get(pid);
    }

    public static Sandbox getCallingSandbox() {
        Sandbox sb = Sandbox.forPid(Binder.getCallingPid());
        if (sb == null) {
            throw new IllegalStateException("Not calling from a sandbox");
        }

        if (sb.getRunningCallRecord() == null) {
            throw new IllegalStateException("Not currently running a SODA in this sandbox");
        }
        return sb;
    }

    public static boolean isCallingFromSandbox() {
        Sandbox sb = Sandbox.forPid(Binder.getCallingPid());
        return (sb != null && sb.getRunningCallRecord() != null);
    }

    public static TaintSet getCallingTaint() {
        if (isCallingFromSandbox()) {
            return getCallingSandbox().getTaints();
        } else {
            return TaintSet.EMPTY;
        }
    }

    private static final EventHandler s_pidConnectedHandler, s_pidDisconnectedHandler;

    static {
        s_pidConnectedHandler = g_onConnected.register(Sandbox.class, new EventHandler() {
            @Override
            public boolean onEvent(String event, Sandbox sender, Object args) throws Exception {
                s_mPidMap.putIfAbsent(sender.getPid(), sender);
                return false;
            }
        });
        s_pidDisconnectedHandler = g_onDisconnected.register(Sandbox.class, new EventHandler() {
            @Override
            public boolean onEvent(String event, Sandbox sender, Object args) throws Exception {
                s_mPidMap.remove(sender.mPid, sender);
                return false;
            }
        });
    }
    //endregion

    private static final Sandbox[] s_mSandboxesById = new Sandbox[OASISConstants.NUM_SANDBOXES];
    private static final Bundle s_mExtrasBundle = new Bundle(2);

    private static final Set<String> s_mKnownPackages = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public static void forgetKnownPackage(String packageName) {
        s_mKnownPackages.remove(packageName);
    }

    public static Sandbox get(int id) {
        Validate.validIndex(s_mSandboxesById, id, "Invalid sandbox ID %d", id);
        synchronized (s_mSandboxesById) {
            Sandbox sb = s_mSandboxesById[id];
            if (sb == null) {
                sb = new Sandbox(id);
                s_mSandboxesById[id] = sb;

                if (s_mExtrasBundle.isEmpty()) {
                    s_mExtrasBundle.putBinder(SandboxService.EXTRA_TRUSTED_API, TrustedAPI.getInstance().asBinder());
                    s_mExtrasBundle.putBinder(SandboxService.EXTRA_ROOT_SERVICE, FlowfenceApplication.getInstance().getBinder().asBinder());
                }
            }
            return sb;
        }
    }

    private static final class ReferenceLeakedException extends RuntimeException {
        public ReferenceLeakedException() {
            super("Caller leaked sandbox reference, allocated here");
        }
    }

    private final class ReferenceHolder {
        private static final String LEAK_MESSAGE = "Sandbox reference leaked";
        private final AtomicBoolean isClosed;
        private final ReferenceLeakedException creator;

        public ReferenceHolder() {
            this.isClosed = new AtomicBoolean(false);

            synchronized (mSync) {
                addRefLocked();
            }

            if (BuildConfig.DEBUG) {
                creator = new ReferenceLeakedException();
            } else {
                creator = null;
            }
        }

        /**
         * Close a ReferenceHolder and drop the corresponding reference.
         * @param strongReferent The object that is the key for this ReferenceHolder.
         */
        public void closeLocked() {
            if (!isClosed.getAndSet(true)) {
                releaseLocked();
            } else {
                Log.w(TAG, "Sandbox reference closed multiple times", new RuntimeException());
            }
        }

        /**
         * Handle a leaked ReferenceHolder.
         * @throws Throwable
         */
        @Override
        public void finalize() throws Throwable {
            try {
                if (!isClosed.getAndSet(true)) {
                    if (creator != null) {
                        Log.w(TAG, LEAK_MESSAGE, creator);
                    } else {
                        Log.w(TAG, LEAK_MESSAGE);
                    }
                    synchronized (mSync) {
                        releaseLocked();
                    }
                }
            } finally {
                super.finalize();
            }
        }

        public ReferenceLeakedException getCreator() {
            return creator;
        }
    }

    private final int mID;
    private final ComponentName mComponent;
    private final ServiceConnection mConnection;
    private final FlowfenceApplication mApplication;
    // Invariant: mStartCount > 0 <=> service is bound or is in the process of doing so.
    private final WeakHashMap<Object, ReferenceHolder> mStarts = new WeakHashMap<>();
    private int mStartCount = 0;
    // Lock controls access to shared state; CV is used to wait for startup.
    // Invariant: mSync is open <=> service is successfully bound.
    private final ConditionVariable mSync = new ConditionVariable();
    private final ConditionVariable mCanRestart = new ConditionVariable(true);
    private final HashSet<String> mKnownPackages = new HashSet<>();
    private final WeakHashMap<Handle, ISandboxObject> mUnmarshalledObjects = new WeakHashMap<>();

    private ISandboxService mSandboxService;
    private int mPid;
    private TaintSet mTaintSet;
    private final Object mTaintLock = new Object();
    private String mAssignedPackage;
    private boolean mIsRestarting;

    private CallRecord mCurrentlyRunning;

    private Sandbox(int id) {
        mID = id;
        mApplication = FlowfenceApplication.getInstance();
        try {
            Class<?> clazz = Class.forName(String.format(SandboxService.SERVICE_FORMAT, id));
            PackageManager pm = mApplication.getPackageManager();
            mComponent = new ComponentName(mApplication, clazz);
            ServiceInfo svcInfo = pm.getServiceInfo(mComponent, 0);
            if ((svcInfo.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) == 0) {
                throw new RuntimeException("Sandbox "+id+" is not isolated!");
            }
        } catch (ClassNotFoundException cnfe) {
            Log.e(TAG, "Could not load sandbox class", cnfe);
            throw new RuntimeException(cnfe);
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, "Service class not declared in manifest", nnfe);
            throw new RuntimeException(nnfe);
        }

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                handleConnected(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                handleDisconnected();
            }
        };

        mSandboxService = null;
        mTaintSet = null;
        mIsRestarting = false;

        g_onCreated.fire(this, null);
    }

    private boolean isStartedLocked() {
        return (mStartCount > 0);
    }

    private boolean isConnectedLocked() {
        return (mSandboxService != null && !mIsRestarting);
    }

    private void handleConnected(IBinder service) {
        synchronized (mSync) {
            if (isConnectedLocked()) {
                Log.w(TAG, "Connected while already connected");
            }

            if (localLOGD) {
                Log.d(TAG, String.format("Service connected for sandbox %d", mID));
            }
            mSandboxService = ISandboxService.Stub.asInterface(service);
            mTaintSet = TaintSet.EMPTY;
            mAssignedPackage = null;
            mCurrentlyRunning = null;
            mKnownPackages.addAll(s_mKnownPackages);
            try {
                mPid = mSandboxService.getPid();
            } catch (RemoteException e) {
                Log.wtf(TAG, e);
                throw new RuntimeException(e);
            } finally {
                mSync.open();
            }
        }
        onConnected.fire(this, null);
    }

    private void handleDisconnected() {
        boolean shouldRebind = false;
        synchronized (mSync) {
            mSync.close();

            if (!mIsRestarting) {
                if (!isConnectedLocked()) {
                    Log.w(TAG, "Disconnected while already disconnected");
                }

                if (mStartCount > 0) {
                    // The sandbox disconnected, but there was a positive start count.
                    // This probably means the sandbox crashed.
                    // Restart it to limit the damage.
                    Log.e(TAG, "Unexpected disconnect, sandbox " + mID);
                    shouldRebind = true;
                }
            }

            if (localLOGD) {
                Log.d(TAG, String.format("Service disconnected for sandbox %d", mID));
            }
            mSandboxService = null;
            mTaintSet = null;
            mAssignedPackage = null;
            mKnownPackages.clear();
            mUnmarshalledObjects.clear();
            mCurrentlyRunning = null;
        }
        onDisconnected.fire(this, null);
        if (shouldRebind) {
            bind();
        }
    }

    private void bind() {
        onBeforeConnect.fire(this, null);
        int flags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_DEBUG_UNBIND;
        if (localLOGD) {
            Log.d(TAG, "binding: " + this);
        }
        String[] packages = new String[s_mKnownPackages.size()];
        packages = s_mKnownPackages.toArray(packages);
        Intent bindIntent = new Intent()
                .setComponent(mComponent)
                .putExtras(s_mExtrasBundle)
                .putExtra(SandboxService.EXTRA_KNOWN_PACKAGES, packages)
                .putExtra(SandboxService.EXTRA_SANDBOX_ID, mID);

        if (!mApplication.bindService(bindIntent, mConnection, flags)) {
            Log.e(TAG, "Couldn't bind to sandbox " + mID);
        }
    }

    public void start(Object key) {
        Objects.requireNonNull(key);
        synchronized (mSync) {
            ReferenceHolder holder = mStarts.get(key);
            if (holder != null) {
                String message = "Starting sandbox multiple times with key "+key;
                ReferenceLeakedException creator = holder.getCreator();
                IllegalStateException e = new IllegalStateException(message, creator);
                Log.e(TAG, "Starting sandbox multiple times", e);
                throw e;
            } else {
                mStarts.put(key, new ReferenceHolder());
            }
        }
    }

    private void addRefLocked() {
        if (localLOGV) {
            Log.v(TAG, "addRef: " + this, new RuntimeException());
        }
        if (mStartCount++ == 0) {
            bind();
        }
    }

    public void waitForStartupComplete() {
        synchronized (mSync) {
            if (localLOGD && !isConnectedLocked()) {
                Log.d(TAG, "waiting for startup: " + this, new RuntimeException());
            }
            mSync.block();
        }
    }

    public void stop(Object key) {
        synchronized (mSync) {
            ReferenceHolder holder = mStarts.remove(key);
            if (holder == null) {
                String message = "Stopping sandbox without starting for key "+key;
                IllegalStateException e = new IllegalStateException(message);
                Log.e(TAG, "Stopping sandbox without starting", e);
            } else {
                holder.closeLocked();
            }
        }
    }

    private void releaseLocked() {
        if (localLOGV) {
            Log.v(TAG, "release: " + this, new RuntimeException());
        }
        int newCount = --mStartCount;
        if (newCount < 0) {
            Log.wtf(TAG, String.format("Sandbox %d stopped without starting %d times", mID, -newCount));
        } else if (newCount == 0) {
            unbind();
        }
    }

    private final Runnable mRestartRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mSync) {
                unbind();
                bind();
                mIsRestarting = false;
                // Drop the reference we grabbed earlier in the call to restart().
                releaseLocked();
            }
        }
    };

    public void restart() {
        if (localLOGD) {
            Log.d(TAG, "restart: " + this);
        }

        synchronized (mSync) {
            if (mStartCount == 0 || mIsRestarting) {
                // We're already stopped or restarting, no need to restart it again.
                return;
            }
            // Take a reference so that intervening stops and starts don't mess with the
            // unbind/bind pair that we're doing in the restart runnable.
            addRefLocked();
            mIsRestarting = true;
            waitForStartupComplete();
            mSync.close();
        }

        FlowfenceApplication.getInstance().getBackgroundExecutor().submit(mRestartRunnable);
    }

    private final int DEATH_PING_INTERVAL = 50; // ping every 50 ms
    private final int DEATH_PING_MAX = 300;     // 300 total pings = 15 s
    private void unbind() {
        if (localLOGD) {
            Log.d(TAG, "unbind: "+this);
        }
        onBeforeDisconnect.fire(this, null);
        ISandboxService sandbox;
        synchronized (mSync) {
            sandbox = mSandboxService;
            mApplication.unbindService(mConnection);
        }

        if (sandbox != null) {
            handleDisconnected();
        } else {
            return;
        }

        synchronized (mSync) {
            // Ask it to terminate itself.
            try {
                IBinder binder = sandbox.asBinder();
                sandbox.kill();

                if (!binder.isBinderAlive()) {
                    return;
                }

                int timeout = DEATH_PING_MAX;
                while (--timeout >= 0) {
                    if (!binder.pingBinder() || !binder.isBinderAlive()) {
                        return;
                    }
                    SystemClock.sleep(DEATH_PING_INTERVAL);
                }
                throw new SecurityException("Sandbox process has not died");
            } catch (RemoteException e) {
                // Object's already dead, or we're getting a spurious TransactionTooLarge.
            }
        }
    }

    private void switchSandboxesLocked(CallRecord record, boolean isStarting) {
        if (mCurrentlyRunning != (isStarting ? null : record)) {
            String message = String.format("%s tried to %s executing %s",
                    this, isStarting ? "start" : "finish", record);
            SandboxInUseException e = new SandboxInUseException(message);
            Log.e(TAG, Log.getStackTraceString(e));
            throw e;
        }
        mCurrentlyRunning = (isStarting ? record : null);
    }

    /*package*/ void beginExecute(CallRecord record) {
        synchronized (mSync) {
            SodaDescriptor desc = record.getSODA().getDescriptor();
            checkConnected();
            checkAssignedPackageLocked(desc);
            mAssignedPackage = desc.definingClass.getPackageName();
            start(record);
            switchSandboxesLocked(record, true);
        }
        onExecutionStart.fire(this, record);
    }

    /*package*/ void endExecute(CallRecord record) {
        onExecutionFinish.fire(this, record);
        synchronized (mSync) {
            stop(record);
            switchSandboxesLocked(record, false);
        }
    }

    private ISandboxService getService() {
        synchronized (mSync) {
            checkStarted();
            if (!isConnectedLocked()) {
                waitForStartupComplete();
            }
            return mSandboxService;
        }
    }

    private void checkStarted() {
        if (!isStartedLocked()) {
            throw new IllegalStateException("Sandbox not started");
        }
    }

    private void checkConnected() {
        checkStarted();
        if (!isConnectedLocked()) {
            waitForStartupComplete();
        }
    }

    private void checkAssignedPackageLocked(SodaDescriptor descriptor) {
        String packageName = descriptor.definingClass.getPackageName();
        if (mAssignedPackage != null && !mAssignedPackage.equals(packageName)) {
            throw new SandboxInUseException(String.format(
                    "%s can't resolve '%s', since it's already assigned to package '%s",
                    this.toString(), descriptor, mAssignedPackage));
        }
        mKnownPackages.add(packageName);
        s_mKnownPackages.add(packageName);
    }

    public int getPid() {
        synchronized (mSync) {
            checkConnected();
            return mPid;
        }
    }

    public TaintSet getTaints() {
        synchronized (mSync) {
            if (isConnectedLocked()) {
                return mTaintSet;
            } else {
                return TaintSet.EMPTY;
            }
        }
    }

    public String getAssignedPackage() {
        synchronized (mSync) {
            if (isConnectedLocked()) {
                return mAssignedPackage;
            } else {
                return null;
            }
        }
    }

    public CallRecord getRunningCallRecord() {
        synchronized (mSync) {
            if (isConnectedLocked()) {
                return mCurrentlyRunning;
            } else {
                return null;
            }
        }
    }

    public boolean hasLoadedPackage(String packageName) {
        synchronized (mSync) {
            checkConnected();
            return mKnownPackages.contains(packageName);
        }
    }

    public boolean addTaint(TaintSet taint) {
        synchronized (mTaintLock) {
            boolean wasTainted;
            synchronized (mSync) {
                checkConnected();
                wasTainted = !taint.isSubsetOf(mTaintSet);
            }
            if (!wasTainted) {
                return false;
            }
            onBeforeTaintAdd.fire(this, taint);
            synchronized (mSync) {
                mTaintSet = mTaintSet.asBuilder().unionWith(taint).build();
            }
            if (localLOGD) {
                Log.d(TAG, "Sandbox " + mID + " now tainted with " + mTaintSet.toString());
            }
            onTaintAdded.fire(this, taint);
            return true;
        }
    }

    public TaintSet removeTaint(TaintSet taintsToRemove, Set<String> allowedPackages) {
        synchronized (mTaintLock) {
            synchronized (mSync) {
                checkConnected();
            }

            TaintSet.Builder removedBuilder = new TaintSet.Builder();
            TaintSet.Builder newTaint = mTaintSet.asBuilder();
            for (ComponentName name : taintsToRemove.asMap().keySet()) {
                if (allowedPackages.contains(name.getPackageName()) && mTaintSet.isTaintedWith(name)) {
                    removedBuilder.addTaint(name, mTaintSet.getTaintAmount(name));
                    newTaint.removeTaint(name);
                }
            }

            TaintSet removedSet = removedBuilder.build();
            if (TaintSet.EMPTY.equals(removedSet)) {
                return TaintSet.EMPTY;
            }

            onBeforeTaintRemove.fire(this, removedSet);
            synchronized (mSync) {
                mTaintSet = newTaint.build();
            }
            if (localLOGD) {
                Log.d(TAG, "After clean, sandbox "+mID+" now tainted with "+mTaintSet);
            }
            onTaintRemoved.fire(this, removedSet);
            return removedSet;
        }
    }

    public IResolvedSoda resolve(SodaDescriptor descriptor, boolean bestMatch, SodaDetails details) throws Exception {
        ResolvedSodaExceptionResult result;
        synchronized (mSync) {
            checkConnected();
            checkAssignedPackageLocked(descriptor);
            result = getService().resolveSoda(descriptor, bestMatch, details);
        }
        result.throwChecked();
        return result.getResult();
    }

    public void registerUnmarshalledObject(Handle h, ISandboxObject sbo) {
        synchronized (mUnmarshalledObjects) {
            mUnmarshalledObjects.put(h, sbo);
        }
    }

    public void unregisterUnmarshalledObject(Handle h) {
        synchronized (mUnmarshalledObjects) {
            mUnmarshalledObjects.remove(h);
        }
    }

    public int countUnmarshalledObjects() {
        synchronized (mUnmarshalledObjects) {
            return mUnmarshalledObjects.size();
        }
    }

    @Override
    public String toString() {
        synchronized (mSync) {
            String status = isConnectedLocked() ? "connected" :
                            isStartedLocked() ? "starting" : "disconnected";
            String running = (mCurrentlyRunning != null) ? "running "+mCurrentlyRunning : "idle";
            return String.format("Sandbox[%d]{refs=%d:%s %s %s}",
                                 mID,
                                 mStartCount,
                                 mStarts.keySet(),
                                 status,
                                 running);
        }
    }

    @Override
    public int hashCode() {
        return mID;
    }

    public int getID() {
        return mID;
    }

    @Override
    public int compareTo(Sandbox other) {
        Objects.requireNonNull(other);
        return mID - other.mID;
    }

    public Debug.MemoryInfo getMemoryInfo() throws RemoteException {
        synchronized (mSync) {
            if (isConnectedLocked()) {
                return mSandboxService.dumpMemoryInfo();
            } else {
                return null;
            }
        }
    }

    public void gc() throws RemoteException {
        synchronized (mSync) {
            if (isConnectedLocked()) {
                mSandboxService.gc();
            }
        }
    }
}

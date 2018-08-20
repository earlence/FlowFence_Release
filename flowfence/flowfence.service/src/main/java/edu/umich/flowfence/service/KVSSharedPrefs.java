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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.kvs.IRemoteSharedPrefs;
import edu.umich.flowfence.kvs.IRemoteSharedPrefsEditor;
import edu.umich.flowfence.kvs.IRemoteSharedPrefsListener;

public final class KVSSharedPrefs extends IRemoteSharedPrefs.Stub implements AutoCloseable {
    private static final String TAG = "OASIS.SharedPrefs.Impl";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private static final char SEPARATOR = ':';
    private static final String DATA = "data";
    private static final String TAINT = "taint";
    private static final String CONFIG = "config";
    private static final String TAINT_SET = "taint-set";

    private static final String CONFIG_PUBLIC_READABLE = "isPublicReadable";
    private static final String CONFIG_PUBLIC_WRITABLE = "isPublicWritable";

    private static final String PLATFORM_NAME_PREFIX = "kvs" + SEPARATOR;

    private final NamespaceSharedPrefs mPrefs;
    private final FlowfenceApplication mContext;
    private final Sandbox mSandbox;
    private final String mOwningPackage;
    private final String mStoreName;
    private final boolean canReadPublic;
    private final boolean canWritePublic;
    private final boolean isReadable;
    private final boolean isWritable;

    private boolean isClosed;


    public final class Editor extends IRemoteSharedPrefsEditor.Stub {
        // Anything in this set has been modified, and will have sandbox taint added on commit.
        // The values represent additional taint to be added, beyond the sandbox taint.
        // Maps to null for deleted keys.
        private final HashMap<String, TaintSet.Builder> mPendingChanges = new HashMap<>();
        private final NamespaceSharedPrefs.Editor mEditor = mPrefs.edit();
        private boolean isCommitted = false;

        private void throwIfCommitted() {
            if (isCommitted) {
                throw new IllegalStateException("Already committed");
            }
        }

        private synchronized TaintSet.Builder markModified(String key) {
            TaintSet.Builder ts = mPendingChanges.get(key);
            if (ts == null) {
                // Either this key was deleted, or it's never been seen before.
                // Either way, it gets a new taint label.
                ts = new TaintSet.Builder();
                mPendingChanges.put(key, ts);
            }
            return ts;
        }

        private synchronized void addTaintFromSandbox(String key) {
            throwIfCommitted();
            markModified(key);
        }

        @Override
        public synchronized void putString(String key, String value) {
            addTaintFromSandbox(key);
            mEditor.putString(DATA, key, value);
        }

        @Override
        public synchronized void putStringSet(String key, List<String> values) {
            addTaintFromSandbox(key);
            mEditor.putStringSet(DATA, key, new HashSet<>(values));
        }

        @Override
        public synchronized void putInt(String key, int value) {
            addTaintFromSandbox(key);
            mEditor.putInt(DATA, key, value);
        }

        @Override
        public synchronized void putLong(String key, long value) {
            addTaintFromSandbox(key);
            mEditor.putLong(DATA, key, value);
        }

        @Override
        public synchronized void putFloat(String key, float value) {
            addTaintFromSandbox(key);
            mEditor.putFloat(DATA, key, value);
        }

        @Override
        public synchronized void putBoolean(String key, boolean value) {
            addTaintFromSandbox(key);
            mEditor.putBoolean(DATA, key, value);
        }

        @Override
        public synchronized void addTaint(String key, TaintSet taint) {
            markModified(key).unionWith(taint);
        }

        @Override
        public synchronized void addTaintToAll(TaintSet taint) {
            // Mark all keys we haven't seen as modified.
            // Deleted keys are considered seen, so they'll be skipped.
            for (ImmutablePair<String, String> key : mPrefs.getAll().keySet()) {
                if (DATA.equals(key.getLeft())) {
                    if (!mPendingChanges.containsKey(key.getRight())) {
                        markModified(key.getRight());
                    }
                }
            }

            // Add taint to all modified keys.
            for (TaintSet.Builder ts : mPendingChanges.values()) {
                if (ts != null) {
                    ts.unionWith(taint);
                }
            }
        }

        @Override
        public synchronized void remove(String key) {
            throwIfCommitted();
            mPendingChanges.put(key, null);
            mEditor.remove(DATA, key);
        }

        @Override
        public synchronized void clear() {
            throwIfCommitted();
            mPendingChanges.clear();
            for (ImmutablePair<String, String> pair : mPrefs.getAll().keySet()) {
                if (DATA.equals(pair.getLeft())) {
                    mPendingChanges.put(pair.getRight(), null);
                    mEditor.remove(pair.getLeft(), pair.getRight());
                }
            }
        }

        @SuppressWarnings("unchecked")
        private synchronized void prepareChanges() {
            for (Map.Entry<String, TaintSet.Builder> entry : mPendingChanges.entrySet()) {
                String key = entry.getKey();
                TaintSet.Builder builder = entry.getValue();
                TaintSet ts;
                if (builder == null) {
                    ts = (mSandbox != null) ? mSandbox.getTaints() : TaintSet.EMPTY;
                } else {
                    if (mSandbox != null) {
                        builder.unionWith(mSandbox.getTaints());
                    }
                    ts = builder.build();
                }

                if (localLOGV) {
                    Log.v(TAG, "Tainting " + mStoreName + '/' + key + " with " + ts);
                }
                if (ts.equals(TaintSet.EMPTY)) {
                    mEditor.remove(TAINT, key);
                } else {
                    mEditor.putTaint(TAINT, key, ts);
                }
            }

            mEditor.putBoolean(CONFIG, CONFIG_PUBLIC_READABLE, canReadPublic)
                   .putBoolean(CONFIG, CONFIG_PUBLIC_WRITABLE, canWritePublic);
            isCommitted = true;
        }

        @Override
        public synchronized boolean commit() {
            // Synchronize on external SharedPrefs, to ensure that we don't get
            // the taint tag from before and the data from after (or vice-versa).
            synchronized (KVSSharedPrefs.this) {
                prepareChanges();
                return mEditor.commit();
            }
        }

        @Override
        public void apply() {
            synchronized (KVSSharedPrefs.this) {
                prepareChanges();
                mEditor.apply();
            }
        }
    }

    private SharedPreferences getPlatformSharedPrefs(String store) {
        final String prefsName = PLATFORM_NAME_PREFIX + mOwningPackage + SEPARATOR + store;
        return mContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    @SuppressWarnings("deprecation")
    public KVSSharedPrefs(Sandbox sandbox,
                          String owningPackage, String callingPackage,
                          String storeName, int mode) {
        mContext = FlowfenceApplication.getInstance();
        mSandbox = sandbox;
        mStoreName = storeName;
        mOwningPackage = mContext.checkPackageName(owningPackage);

        SharedPreferences sp = getPlatformSharedPrefs(storeName);
        mPrefs = NamespaceSharedPrefs.get(sp, TAINT_SET, TAINT);

        // Handle read and write permissions, as previously configured.
        boolean configReadPublic = mPrefs.getBoolean(CONFIG, CONFIG_PUBLIC_READABLE, false);
        boolean configWritePublic = mPrefs.getBoolean(CONFIG, CONFIG_PUBLIC_WRITABLE, false);

        if (localLOGD) {
            Log.d(TAG, String.format("Store '%s', owner '%s', caller '%s'", storeName, owningPackage, callingPackage));
        }
        // Are we trying to load this package's prefs, or something else?
        boolean isSamePackage = Objects.equals(owningPackage, callingPackage);

        // Set the readable and writable flags for this object...
        isReadable = isSamePackage || configReadPublic;
        isWritable = isSamePackage || configWritePublic;

        // ...And set the flags that will be written on update.
        canReadPublic = (mode & Context.MODE_WORLD_READABLE) != 0;
        canWritePublic = (mode & Context.MODE_WORLD_WRITEABLE) != 0;

        isClosed = false;
    }

    private void checkClosed() {
        if (isClosed) {
            throw new IllegalStateException("SharedPreferences has been closed");
        }
    }

    private void checkRead() {
        checkClosed();
        if (!isReadable) {
            throw new SecurityException("Can't read this SharedPreferences");
        }
    }

    private void checkReadKey(String key) {
        checkRead();
        if (mSandbox != null) {
            mSandbox.addTaint(mPrefs.getTaint(TAINT, key, TaintSet.EMPTY));
        }
    }

    /**
     * Wrap a {@link ClassCastException} to be thrown across the wire.
     * @param cce The ClassCastException to wrap.
     * @return An exception capable of being handled by {@link android.os.Parcel#writeException(Exception)}.
     */
    private RuntimeException wrapClassCast(final ClassCastException cce) {
        return new IllegalArgumentException(cce.getMessage());
    }

    @Override
    public synchronized boolean contains(String key) {
        checkReadKey(key);
        return mPrefs.contains(DATA, key);
    }

    @Override
    public synchronized Editor edit() {
        checkClosed();
        if (!isWritable) {
            throw new SecurityException("This SharedPreferences cannot be modified");
        }
        return new Editor();
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        checkReadKey(key);
        try {
            return mPrefs.getBoolean(DATA, key, defValue);
        } catch (ClassCastException cce) {
            throw wrapClassCast(cce);
        }
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        checkReadKey(key);
        try {
            return mPrefs.getFloat(DATA, key, defValue);
        } catch (ClassCastException cce) {
            throw wrapClassCast(cce);
        }
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        checkReadKey(key);
        try {
            return mPrefs.getInt(DATA, key, defValue);
        } catch (ClassCastException cce) {
            throw wrapClassCast(cce);
        }
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        checkReadKey(key);
        try {
            return mPrefs.getLong(DATA, key, defValue);
        } catch (ClassCastException cce) {
            throw wrapClassCast(cce);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Map<String, ?> getAll() {
        checkRead();
        TaintSet.Builder tsb = new TaintSet.Builder();
        Map<String, Object> data = new HashMap<>();

        for (Map.Entry<ImmutablePair<String, String>, ?> entry : mPrefs.getAll().entrySet()) {
            ImmutablePair<String, String> typeAndKey = entry.getKey();
            if (DATA.equals(typeAndKey.getLeft())) {
                // If this is a data entry, remember it.
                data.put(typeAndKey.getRight(), entry.getValue());
            } else if (TAINT.equals(typeAndKey.getKey())) {
                // If this is a taint entry, add it to the overall taint label.
                tsb.unionWith((TaintSet)entry.getValue());
            }
        }

        if (mSandbox != null) {
            mSandbox.addTaint(tsb.build());
        }

        return data;
    }

    @Override
    public synchronized String getString(String key) {
        checkReadKey(key);
        try {
            return mPrefs.getString(DATA, key, null);
        } catch (ClassCastException cce) {
            throw wrapClassCast(cce);
        }
    }

    @Override
    public synchronized List<String> getStringSet(String key) {
        checkReadKey(key);
        try {
            Set<String> retSet = mPrefs.getStringSet(DATA, key, null);
            return (retSet != null) ? new ArrayList<>(retSet) : null;
        } catch (ClassCastException cce) {
            throw wrapClassCast(cce);
        }
    }

    @Override
    public synchronized void close() {
        isClosed = true;
    }

    @Override
    public String toString() {
        return "TaintableSharedPrefs{" +
                "mSandbox=" + mSandbox +
                ", mOwningPackage='" + mOwningPackage + '\'' +
                ", isClosed=" + isClosed +
                '}';
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(IRemoteSharedPrefsListener listener) throws RemoteException {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(IRemoteSharedPrefsListener listener) throws RemoteException {
        throw new IllegalStateException("Not implemented");
    }
}

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

import android.content.SharedPreferences;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import edu.umich.flowfence.common.TaintSet;

public class NamespaceSharedPrefs {
    private static final String TAG = "FF.SharedPrefs.Base";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);
    private static final char SEPARATOR = ':';

    private static String NS(String namespace, String key) {
        return namespace + SEPARATOR + key;
    }

    private static String NS(Map.Entry<? extends String, ? extends String> pair) {
        return NS(pair.getKey(), pair.getValue());
    }

    private static final int TAINT_ID_EMPTY = -1;

    private static final WeakHashMap<SharedPreferences, WeakReference<NamespaceSharedPrefs>>
            g_mPrefsLookup = new WeakHashMap<>();

    private final Set<String> mTaintNamespaces;
    private final String mTaintSetNamespace;
    private final SharedPreferences mBasePrefs;
    private final SparseArray<TaintSet> mKnownTaints;
    private final SharedPreferences.OnSharedPreferenceChangeListener mListener;

    private String getTaintSetKey(int id) {
        return NS(mTaintSetNamespace, Integer.toString(id));
    }

    private static ImmutablePair<String, String> getTypeAndKey(String key) {
        int index = key.indexOf(SEPARATOR);
        return ImmutablePair.of(key.substring(0, index), key.substring(index + 1));
    }

    public static NamespaceSharedPrefs get(SharedPreferences basePrefs, String taintSetNamespace,
                                           String... taintNamespaces) {
        Objects.requireNonNull(taintSetNamespace);
        taintNamespaces = ArrayUtils.nullToEmpty(taintNamespaces);
        if (taintNamespaces.length == 0) {
            throw new IllegalArgumentException("Need at least one taint namespace!");
        }
        Set<String> taintNamespaceSet = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(taintNamespaces)));
        if (taintNamespaceSet.contains(taintSetNamespace)) {
            throw new IllegalArgumentException("Taint set namespace also a taint namespace");
        }
        synchronized (g_mPrefsLookup) {
            NamespaceSharedPrefs prefsStrong = null;
            WeakReference<NamespaceSharedPrefs> prefsWeak = g_mPrefsLookup.get(basePrefs);
            if (prefsWeak != null) {
                prefsStrong = prefsWeak.get();
            }
            if (prefsStrong != null) {
                if (!taintSetNamespace.equals(prefsStrong.mTaintSetNamespace) ||
                        !taintNamespaceSet.equals(prefsStrong.mTaintNamespaces)) {
                    Log.w(TAG, String.format("Inconsistent initialization: "+
                            "initialized with TS=%s T=%s, retrieved with TS=%s T=%s",
                            prefsStrong.mTaintSetNamespace, prefsStrong.mTaintNamespaces,
                            taintSetNamespace, taintNamespaceSet));
                }
            } else {
                prefsStrong = new NamespaceSharedPrefs(basePrefs, taintSetNamespace, taintNamespaceSet);
                prefsWeak = new WeakReference<>(prefsStrong);
                g_mPrefsLookup.put(basePrefs, prefsWeak);
            }
            return prefsStrong;
        }
    }

    private NamespaceSharedPrefs(SharedPreferences basePrefs, String taintSetNamespace, Set<String> taintNamespaces) {
        mTaintSetNamespace = taintSetNamespace;
        mTaintNamespaces = taintNamespaces;
        mBasePrefs = basePrefs;
        mKnownTaints = new SparseArray<>();
        mListener = registerListener();
    }

    private void checkNotTaintNamespace(String namespace) {
        checkTaintNamespace(namespace, false);
    }

    private void checkIsTaintNamespace(String namespace) {
        checkTaintNamespace(namespace, true);
    }

    private void checkTaintNamespace(String namespace, boolean shouldBeTaint) {
        if (mTaintSetNamespace.equals(namespace) ||
                (mTaintNamespaces.contains(namespace) != shouldBeTaint)) {
            throw new IllegalArgumentException("Bad namespace "+namespace);
        }
    }

    public synchronized Map<ImmutablePair<String, String>, ?> getAll() {
        Map<String, ?> all = mBasePrefs.getAll();
        Map<ImmutablePair<String, String>, Object> rv = new HashMap<>(all.size());
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            ImmutablePair<String, String> typeAndKey = getTypeAndKey(entry.getKey());
            Object value;
            if (mTaintSetNamespace.equals(typeAndKey.getLeft())) {
                continue;
            } else if (mTaintNamespaces.contains(typeAndKey.getLeft())) {
                // Replace this with the appropriate TaintSet.
                value = getTaintById((Integer)entry.getValue());
            } else {
                value = entry.getValue();
            }
            rv.put(typeAndKey, value);
        }
        return rv;
    }

    public synchronized Map<String, ?> getAll(String namespace) {
        if (mTaintSetNamespace.equals(Objects.requireNonNull(namespace))) {
            throw new IllegalArgumentException("Bad namespace "+namespace);
        }

        final Map<String, ?> all = mBasePrefs.getAll();
        final Map<String, Object> rv = new HashMap<>();
        final boolean isTaintNS = mTaintNamespaces.contains(namespace);

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            final ImmutablePair<String, String> typeAndKey = getTypeAndKey(entry.getKey());
            if (namespace.equals(typeAndKey.getLeft())) {
                final Object value;
                if (isTaintNS) {
                    value = getTaintById((Integer)entry.getValue());
                } else {
                    value = entry.getValue();
                }
                rv.put(typeAndKey.getRight(), value);
            }
        }

        return rv;
    }

    public String getString(String namespace, String key, String defValue) {
        checkNotTaintNamespace(namespace);
        return mBasePrefs.getString(NS(namespace, key), defValue);
    }

    public Set<String> getStringSet(String namespace, String key, Set<String> defValues) {
        checkNotTaintNamespace(namespace);
        return mBasePrefs.getStringSet(NS(namespace, key), defValues);
    }

    public int getInt(String namespace, String key, int defValue) {
        checkNotTaintNamespace(namespace);
        return mBasePrefs.getInt(NS(namespace, key), defValue);
    }

    public long getLong(String namespace, String key, long defValue) {
        checkNotTaintNamespace(namespace);
        return mBasePrefs.getLong(NS(namespace, key), defValue);
    }

    public float getFloat(String namespace, String key, float defValue) {
        checkNotTaintNamespace(namespace);
        return mBasePrefs.getFloat(NS(namespace, key), defValue);
    }

    public boolean getBoolean(String namespace, String key, boolean defValue) {
        checkNotTaintNamespace(namespace);
        return mBasePrefs.getBoolean(NS(namespace, key), defValue);
    }

    private synchronized TaintSet getTaintById(int taintId) {
        TaintSet ts;
        if (taintId != TAINT_ID_EMPTY) {
            ts = mKnownTaints.get(taintId);
            if (ts == null) {
                Set<String> stringSet = mBasePrefs.getStringSet(getTaintSetKey(taintId), null);
                if (stringSet == null) {
                    Log.e(TAG, "Can't look up taint ID " + taintId + ", assuming empty");
                }
                ts = TaintSet.fromStrings(stringSet);
                mKnownTaints.put(taintId, ts);
            }
        } else {
            ts = TaintSet.EMPTY;
        }
        return ts;
    }

    public synchronized TaintSet getTaint(String namespace, String key, TaintSet defValue) {
        checkIsTaintNamespace(namespace);
        int taintId = mBasePrefs.getInt(NS(namespace, key), TAINT_ID_EMPTY);
        TaintSet ts = getTaintById(taintId);
        if (localLOGV) {
            Log.d(TAG, "Taint for key " + key + " = " + ts);
        }
        return ts;
    }

    public TaintSet collectAllTaints(String namespace) {
        checkIsTaintNamespace(namespace);
        Map<String, ?> taintSetMap = getAll(namespace);
        TaintSet.Builder builder = new TaintSet.Builder();
        for (Object taintObj : taintSetMap.values()) {
            builder.unionWith((TaintSet)taintObj);
        }
        return builder.build();
    }

    public boolean contains(String namespace, String key) {
        return mBasePrefs.contains(namespace+SEPARATOR+key);
    }

    public final class Editor {
        private final SharedPreferences.Editor mEditor = mBasePrefs.edit();
        private final HashMap<ImmutablePair<String, String>, TaintSet> mPendingChanges = new HashMap<>();
        private boolean mCleared = false;

        public Editor putString(String namespace, String key, String value) {
            checkNotTaintNamespace(namespace);
            mEditor.putString(NS(namespace, key), value);
            return this;
        }

        public Editor putStringSet(String namespace, String key, Set<String> values) {
            checkNotTaintNamespace(namespace);
            mEditor.putStringSet(NS(namespace, key), values);
            return this;
        }

        public Editor putInt(String namespace, String key, int value) {
            checkNotTaintNamespace(namespace);
            mEditor.putInt(NS(namespace, key), value);
            return this;
        }

        public Editor putLong(String namespace, String key, long value) {
            checkNotTaintNamespace(namespace);
            mEditor.putLong(NS(namespace, key), value);
            return this;
        }

        public Editor putFloat(String namespace, String key, float value) {
            checkNotTaintNamespace(namespace);
            mEditor.putFloat(NS(namespace, key), value);
            return this;
        }

        public Editor putBoolean(String namespace, String key, boolean value) {
            checkNotTaintNamespace(namespace);
            mEditor.putBoolean(NS(namespace, key), value);
            return this;
        }

        public synchronized Editor putTaint(String namespace, String key, TaintSet value) {
            checkIsTaintNamespace(namespace);
            mPendingChanges.put(ImmutablePair.of(namespace, key), value);
            return this;
        }

        public synchronized Editor remove(String namespace, String key) {
            if (mTaintNamespaces.contains(namespace)) {
                mPendingChanges.put(ImmutablePair.of(namespace, key), null);
            }
            mEditor.remove(NS(namespace, key));
            return this;
        }

        public synchronized Editor clear() {
            mCleared = true;
            mEditor.clear();
            return this;
        }

        public synchronized boolean commit() {
            synchronized (NamespaceSharedPrefs.this) {
                prepareChanges();
                return mEditor.commit();
            }
        }

        public void apply() {
            synchronized (NamespaceSharedPrefs.this) {
                prepareChanges();
                mEditor.apply();
            }
        }

        @SuppressWarnings("unchecked")
        private synchronized void prepareChanges() {
            SparseIntArray refCounts = new SparseIntArray();
            // Gather ref counts for current taints.
            if (mCleared) {
                mKnownTaints.clear();
            } else {
                for (Map.Entry<String, ?> entry : mBasePrefs.getAll().entrySet()) {
                    Map.Entry<String, String> pair = getTypeAndKey(entry.getKey());
                    if (mTaintSetNamespace.equals(pair.getKey())) {
                        // A taint with this id exists in the SharedPreferences; keep track of its references.
                        int taintId = Integer.parseInt(pair.getValue());
                        if (refCounts.indexOfKey(taintId) < 0) {
                            refCounts.put(taintId, 0);
                        }
                        if (mKnownTaints.indexOfKey(taintId) < 0) {
                            mKnownTaints.put(taintId, TaintSet.fromStrings((Set<String>) entry.getValue()));
                        }
                    } else if (mTaintNamespaces.contains(pair.getKey())) {
                        // It's referring to something.
                        int taintId = (Integer)entry.getValue();
                        if (taintId != TAINT_ID_EMPTY) {
                            refCounts.put(taintId, refCounts.get(taintId, 0) + 1);
                        }
                    }
                }
            }
            HashMap<TaintSet, Integer> reverseKnownTaints = new HashMap<>();
            for (int i = 0; i < mKnownTaints.size(); i++) {
                int taintId = mKnownTaints.keyAt(i);
                TaintSet knownTaint = mKnownTaints.valueAt(i);
                if (localLOGD) {
                    Log.d(TAG, String.format("Known taint #%d %s, refcount=%d", taintId, knownTaint,
                            refCounts.get(taintId, 0)));
                }
                reverseKnownTaints.put(knownTaint, taintId);
            }

            int nextTaintSetId = 0;
            // Apply taint changes to the taint editor.
            for (Map.Entry<ImmutablePair<String, String>, TaintSet> entry : mPendingChanges.entrySet()) {
                final String namespacedKey = NS(entry.getKey());
                if (!mCleared) {
                    int currentTaintId = mBasePrefs.getInt(namespacedKey, TAINT_ID_EMPTY);
                    if (currentTaintId != TAINT_ID_EMPTY) {
                        refCounts.put(currentTaintId, refCounts.get(currentTaintId) - 1);
                    }
                }
                TaintSet ts = entry.getValue();
                if (ts == null) {
                    mEditor.remove(namespacedKey);
                    continue;
                }

                int newTaintId;
                if (TaintSet.EMPTY.equals(ts)) {
                    newTaintId = TAINT_ID_EMPTY;
                } else {
                    Integer currentKnownTaint = reverseKnownTaints.get(ts);
                    if (currentKnownTaint == null) {
                        while (refCounts.indexOfKey(nextTaintSetId) >= 0) {
                            nextTaintSetId++;
                        }
                        // We found a hole to map ts in at nextTaintSetId.
                        newTaintId = nextTaintSetId;
                        refCounts.put(newTaintId, 1);
                        mKnownTaints.put(newTaintId, ts);
                        reverseKnownTaints.put(ts, newTaintId);
                        mEditor.putStringSet(getTaintSetKey(newTaintId), ts.toStringSet());
                    } else {
                        newTaintId = currentKnownTaint;
                        refCounts.put(newTaintId, refCounts.get(newTaintId)+1);
                    }
                }

                mEditor.putInt(namespacedKey, newTaintId);
            }

            if (!mCleared) {
                // Free up unreferenced taint sets.
                for (int i = 0; i < refCounts.size(); i++) {
                    if (refCounts.valueAt(i) == 0) {
                        int taintId = refCounts.keyAt(i);
                        mEditor.remove(getTaintSetKey(taintId));
                        mKnownTaints.remove(taintId);
                    }
                }
            }

            mCleared = false;
            mPendingChanges.clear();
        }
    }

    public Editor edit() {
        return new Editor();
    }

    public interface OnNamespacePreferenceChangeListener {
        void onNamespacePreferenceChanged(NamespaceSharedPrefs prefs, String namespace, String key);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener registerListener() {
        SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                ImmutablePair<String, String> typeAndKey = getTypeAndKey(key);
                Set<OnNamespacePreferenceChangeListener> listeners;
                synchronized (mListenerMap) {
                    listeners = new HashSet<>(mListenerMap.keySet());
                }
                for (OnNamespacePreferenceChangeListener listener : listeners) {
                    if (listener != null) {
                        listener.onNamespacePreferenceChanged(NamespaceSharedPrefs.this,
                                typeAndKey.getLeft(), typeAndKey.getRight());
                    }
                }
            }
        };
        mBasePrefs.registerOnSharedPreferenceChangeListener(prefListener);
        return prefListener;
    }

    private final WeakHashMap<OnNamespacePreferenceChangeListener, Object> mListenerMap = new WeakHashMap<>();

    public void registerOnNamespacePreferenceChangedListener(OnNamespacePreferenceChangeListener listener) {
        synchronized (mListenerMap) {
            mListenerMap.put(listener, ObjectUtils.NULL);
        }
    }

    public void unregisterOnNamespacePreferenceChangedListener(OnNamespacePreferenceChangeListener listener) {
        synchronized (mListenerMap) {
            mListenerMap.remove(listener);
        }
    }
}

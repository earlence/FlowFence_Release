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

import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umich.flowfence.common.RemoteCallException;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.common.TaintableSharedPreferencesEditor;
import edu.umich.flowfence.kvs.IRemoteSharedPrefs;
import edu.umich.flowfence.kvs.IRemoteSharedPrefsEditor;
import edu.umich.flowfence.kvs.IRemoteSharedPrefsListener;

/*package*/ final class RemoteSharedPrefsWrapper implements SharedPreferences {
    private static final String TAG = "FF.SharedPrefs.Proxy";
    private final IRemoteSharedPrefs mRemote;

    public static final class Editor implements TaintableSharedPreferencesEditor {
        private final IRemoteSharedPrefsEditor mEditor;

        public Editor(IRemoteSharedPrefsEditor editor) {
            if (editor == null) {
                throw new IllegalArgumentException("editor cannot be null");
            }
            mEditor = editor;
        }

        @Override
        public Editor putString(String key, String value) {
            try {
                mEditor.putString(key, value);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            try {
                List<String> valueList = (values != null) ? new ArrayList<>(values) : null;
                mEditor.putStringSet(key, valueList);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            try {
                mEditor.putInt(key, value);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            try {
                mEditor.putLong(key, value);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            try {
                mEditor.putFloat(key, value);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            try {
                mEditor.putBoolean(key, value);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor remove(String key) {
            try {
                mEditor.remove(key);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor clear() {
            try {
                mEditor.clear();
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor addTaint(String key, TaintSet taint) {
            try {
                mEditor.addTaint(key, taint);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public Editor addTaintToAll(TaintSet taint) {
            try {
                mEditor.addTaintToAll(taint);
            } catch (Exception e) {
                handleCallException(e);
            }
            return this;
        }

        @Override
        public boolean commit() {
            try {
                return mEditor.commit();
            } catch (Exception e) {
                handleCallException(e);
                return false;
            }
        }

        @Override
        public void apply() {
            try {
                mEditor.apply();
            } catch (Exception e) {
                handleCallException(e);
            }
        }
    }

    private final class Listener extends IRemoteSharedPrefsListener.Stub {
        private final SharedPreferences.OnSharedPreferenceChangeListener mListener;

        public Listener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("listener cannot be null");
            }
            mListener = listener;
        }

        @Override
        public void onSharedPreferenceChanged(IRemoteSharedPrefs prefs, String key) {
            if (prefs == mRemote) {
                mListener.onSharedPreferenceChanged(RemoteSharedPrefsWrapper.this, key);
            }
        }
    }

    public RemoteSharedPrefsWrapper(IRemoteSharedPrefs prefs) {
        if (prefs == null) {
            throw new IllegalArgumentException("prefs cannot be null");
        }
        mRemote = prefs;
    }

    private static void handleCallException(Exception e) {
        RuntimeException toThrow = null;
        if (e instanceof IllegalArgumentException) {
            // This is a wrapped ClassCastException from TaintableSharedPrefs.
            toThrow = new ClassCastException(e.getMessage());
        } else if (e instanceof RemoteException) {
            toThrow = new RemoteCallException(e);
        } else if (e instanceof RuntimeException) {
            toThrow = (RuntimeException)e;
        } else {
            toThrow = new RuntimeException(e);
        }

        if (toThrow != null) {
            Log.w(TAG, "Exception from remote SharedPrefs instance", toThrow);
            throw toThrow;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ?> getAll() {
        try {
            return (Map<String, ?>)(mRemote.getAll());
        } catch (Exception e) {
            handleCallException(e);
            return Collections.emptyMap();
        }
    }

    @Override
    public String getString(String key, String defValue) {
        try {
            String rv = mRemote.getString(key);
            return (rv != null) ? rv : defValue;
        } catch (Exception e) {
            handleCallException(e);
            return defValue;
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        try {
            List<String> rv = mRemote.getStringSet(key);
            return (rv != null) ? new HashSet<>(rv) : defValues;
        } catch (Exception e) {
            handleCallException(e);
            return defValues;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        try {
            return mRemote.getInt(key, defValue);
        } catch (Exception e) {
            handleCallException(e);
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return mRemote.getLong(key, defValue);
        } catch (Exception e) {
            handleCallException(e);
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return mRemote.getFloat(key, defValue);
        } catch (Exception e) {
            handleCallException(e);
            return defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        try {
            return mRemote.getBoolean(key, defValue);
        } catch (Exception e) {
            handleCallException(e);
            return defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        try {
            return mRemote.contains(key);
        } catch (Exception e) {
            handleCallException(e);
            return false;
        }
    }

    @Override
    public Editor edit() {
        try {
            IRemoteSharedPrefsEditor editor = mRemote.edit();
            return new Editor(editor);
        } catch (Exception e) {
            handleCallException(e);
            return null;
        }
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        // TODO
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        // TODO
    }
}

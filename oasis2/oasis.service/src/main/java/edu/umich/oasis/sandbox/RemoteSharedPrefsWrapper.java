package edu.umich.oasis.sandbox;

import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umich.oasis.common.RemoteCallException;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.common.TaintableSharedPreferencesEditor;
import edu.umich.oasis.kvs.IRemoteSharedPrefs;
import edu.umich.oasis.kvs.IRemoteSharedPrefsEditor;
import edu.umich.oasis.kvs.IRemoteSharedPrefsListener;

/**
 * Created by jpaupore on 10/4/15.
 */
/*package*/ final class RemoteSharedPrefsWrapper implements SharedPreferences {
    private static final String TAG = "OASIS.SharedPrefs.Proxy";
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

// IRemoteSharedPrefs.aidl
package edu.umich.oasis.kvs;

// Declare any non-default types here with import statements
import edu.umich.oasis.kvs.IRemoteSharedPrefsEditor;
import edu.umich.oasis.kvs.IRemoteSharedPrefsListener;

// Shared preference interface.
// Only exposed to sandbox.
interface IRemoteSharedPrefs {
    boolean contains(String key);
    IRemoteSharedPrefsEditor edit();

    Map getAll();
    boolean getBoolean(String key, boolean defValue);
    float getFloat(String key, float defValue);
    int getInt(String key, int defValue);
    long getLong(String key, long defValue);

    // These methods return null if the key is not found.
    String getString(String key);
    List<String> getStringSet(String key);

    void registerOnSharedPreferenceChangeListener(in IRemoteSharedPrefsListener listener);
    void unregisterOnSharedPreferenceChangeListener(in IRemoteSharedPrefsListener listener);
}

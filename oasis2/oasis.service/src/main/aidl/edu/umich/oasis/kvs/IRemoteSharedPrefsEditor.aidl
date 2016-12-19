// IRemoteSharedPrefsEditor.aidl
package edu.umich.oasis.kvs;

// Declare any non-default types here with import statements
import edu.umich.oasis.common.TaintSet;

interface IRemoteSharedPrefsEditor {
    void putBoolean(String key, boolean value);
    void putFloat(String key, float value);
    void putInt(String key, int value);
    void putLong(String key, long value);
    void putString(String key, String value);
    void putStringSet(String key, in List<String> value);

    void remove(String key);
    void clear();

    void addTaint(String key, in TaintSet taint);
    void addTaintToAll(in TaintSet taint);

    boolean commit();
    oneway void apply();
}

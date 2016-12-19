// IRemoteSharedPrefsListener.aidl
package edu.umich.oasis.kvs;

// Declare any non-default types here with import statements
import edu.umich.oasis.kvs.IRemoteSharedPrefs;

interface IRemoteSharedPrefsListener {
    oneway void onSharedPreferenceChanged(in IRemoteSharedPrefs prefs, String key);
}

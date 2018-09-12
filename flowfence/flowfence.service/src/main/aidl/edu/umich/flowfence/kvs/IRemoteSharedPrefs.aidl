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

// IRemoteSharedPrefs.aidl
package edu.umich.flowfence.kvs;

// Declare any non-default types here with import statements
import edu.umich.flowfence.kvs.IRemoteSharedPrefsEditor;
import edu.umich.flowfence.kvs.IRemoteSharedPrefsListener;

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

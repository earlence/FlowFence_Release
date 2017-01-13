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

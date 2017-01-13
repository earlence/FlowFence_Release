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

package edu.umich.oasis.testapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import edu.umich.oasis.common.IDynamicAPI;
import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.common.TaintableSharedPreferencesEditor;

public class KeyValueTest {
    private static final String STORE_NAME = "KeyValueTestStore";
    private static final String KEY_NAME = "testValue";

    public static String getValue() {
        SharedPreferences prefs = OASISContext.getInstance()
                .getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_NAME, "");
    }

    private static final String TAINT = "edu.umich.oasis.testapp/test";
    private static final TaintSet TAINT_SET = new TaintSet.Builder().addTaint(TAINT).build();
    public static void setValue(String value, boolean addTaint) {
        SharedPreferences prefs = OASISContext.getInstance()
                .getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_NAME, value);

        if (addTaint) {
            ((TaintableSharedPreferencesEditor) editor).addTaint(KEY_NAME, TAINT_SET);
        }

        editor.apply();
    }

    public static void toastValue() {
        String value = getValue();
        IDynamicAPI toast = (IDynamicAPI)OASISContext.getInstance().getTrustedAPI("toast");

        toast.invoke("showText", "KVS value: '" + value + "'", Toast.LENGTH_LONG);
    }

    public static void pushValue() {
        String value = getValue();
        IDynamicAPI push = (IDynamicAPI)OASISContext.getInstance().getTrustedAPI("push");

        push.invoke("sendPush", "Test Push", "KVS value = "+value);
    }
}

package edu.umich.oasis.testapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import edu.umich.oasis.common.IDynamicAPI;
import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.common.TaintableSharedPreferencesEditor;

/**
 * Created by jpaupore on 10/4/15.
 */
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

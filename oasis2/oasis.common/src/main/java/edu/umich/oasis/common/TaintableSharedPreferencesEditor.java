package edu.umich.oasis.common;

import android.content.SharedPreferences;

/**
 * Created by jpaupore on 10/5/15.
 */
public interface TaintableSharedPreferencesEditor extends SharedPreferences.Editor {
    TaintableSharedPreferencesEditor addTaint(String key, TaintSet taint);
    TaintableSharedPreferencesEditor addTaintToAll(TaintSet taint);
}

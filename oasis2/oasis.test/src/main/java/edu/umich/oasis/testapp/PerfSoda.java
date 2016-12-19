package edu.umich.oasis.testapp;

import android.util.Log;

import edu.umich.oasis.common.ITaintAPI;
import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.TaintSet;

/**
 * Created by jpaupore on 2/15/16.
 */
public abstract class PerfSoda {
    private PerfSoda() { }

    private static TaintSet TEST_TAINT = TaintSet.singleton("edu.umich.oasis.testapp/test");
    public static void execSoda(boolean shouldTaint, byte[] unusedData) {
        if (shouldTaint) {
            ITaintAPI api = (ITaintAPI)OASISContext.getInstance().getTrustedAPI("taint");
            api.addTaint(TEST_TAINT);
        }
    }
}

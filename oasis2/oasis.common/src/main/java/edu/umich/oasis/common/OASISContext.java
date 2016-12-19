package edu.umich.oasis.common;

import android.content.Context;
import android.content.ContextWrapper;

/**
 * Created by jpaupore on 2/2/15.
 */
public abstract class OASISContext extends ContextWrapper {
    private static OASISContext mInstance = null;

    public static OASISContext getInstance() {
        return mInstance;
    }

    protected static void setInstance(OASISContext context) {
        mInstance = context;
    }

    public static boolean isInSoda() {
        return (mInstance != null);
    }

    protected OASISContext(Context baseCtx) {
        super(baseCtx);
    }

    public abstract Object getTrustedAPI(String apiName);
}

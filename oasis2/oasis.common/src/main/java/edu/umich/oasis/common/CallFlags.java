package edu.umich.oasis.common;

/**
 * Created by jpaupore on 6/5/15.
 */
public final class CallFlags {
    // Flags
    public static final int CALL_SYNC           = 0x00000000;
    public static final int CALL_ASYNC          = 0x80000000;
    public static final int FORCE_SYNC_ASYNC    = 0x40000000;

    public static final int FORCE_SYNC          = CALL_SYNC | FORCE_SYNC_ASYNC;
    public static final int FORCE_ASYNC         = CALL_ASYNC | FORCE_SYNC_ASYNC;

    public static final int NO_RETURN_VALUE     = 0x20000000;
    public static final int FILTER_EXCEPTIONS   = 0x10000000; // TODO
    public static final int OVERRIDE_SANDBOX    = 0x08000000;

    public static final int SANDBOX_NUM_MASK    = (Integer.highestOneBit(OASISConstants.NUM_SANDBOXES-1) << 1) - 1;
}

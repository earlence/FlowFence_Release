package edu.umich.oasis.helpers;

/**
 * Created by earlence on 4/22/15.
 */
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Field;

public abstract class ParcelHelper
{
    private ParcelHelper() { }

    private static final String TAG = "OASIS.ParcelHelper";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    //will return true if seek succeeds. false otherwise.
    public static native int seekToBinder(long nativePtr, int object);
    public static native int getNumObjects(long nativePtr);
    public static native int getDataPos(long nativePtr);
    public static native boolean setDataPos(long nativePtr, int pos);
    public static native void rewriteGDefaultServiceManager(IBinder newbinder);

    public static long getParcelNativePtr(Parcel p)
    {
        long nativePtr = 0L;
        try {
            Field f = p.getClass().getDeclaredField("mNativePtr");
            f.setAccessible(true);
            nativePtr = f.getLong(p);
        } catch (Exception e) {
            Log.e(TAG, "Stuff happened", e);
        }

        return nativePtr;
    }

    public static boolean hasObjects(Parcel p) {
        long nativePtr = getParcelNativePtr(p);
        if (nativePtr == 0) {
            throw new RuntimeException("Can't get native pointer");
        }

        int objectCount = getNumObjects(nativePtr);
        return (objectCount > 0);
    }

    static {
        //System.loadLibrary("parcelhelper");
    }
}
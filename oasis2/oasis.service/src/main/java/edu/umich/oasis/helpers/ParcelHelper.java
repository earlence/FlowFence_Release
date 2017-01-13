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

package edu.umich.oasis.helpers;

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
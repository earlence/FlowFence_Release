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

package edu.umich.flowfence.common;

import android.os.IBinder;
import android.os.MemoryFile;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;

public final class ParceledPayload implements Parcelable {
    private static final String TAG = "FF.ParceledPayload";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    public static boolean canParcelType(Class<?> clazz) {
        return canParcelType(clazz, false);
    }

    public static boolean canParcelType(Class<?> clazz, boolean allowVoid) {
        // All primitives and wrapper types are parcelable, except Character and Void.
        if (ClassUtils.isPrimitiveOrWrapper(clazz)) {
            return (clazz != char.class && clazz != Character.class &&
                    (allowVoid || (clazz != void.class && clazz != Void.class)));
        }
        // String and CharSequence are parcelable.
        if (clazz == String.class || ClassUtils.isAssignable(clazz, CharSequence.class)) {
            return true;
        }
        // Object arrays are parcelable if their component type is parcelable.
        // Primitive boolean[], byte[], int[], and long[] arrays are parcelable.
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            if (componentType.isPrimitive()) {
                return (componentType == int.class ||
                        componentType == long.class ||
                        componentType == byte.class ||
                        componentType == boolean.class);
            } else {
                return canParcelType(componentType, false);
            }
        }
        // Parcelable, obviously, is parcelable.
        // This covers Bundle as well.
        if (ClassUtils.isAssignable(clazz, Parcelable.class)) {
            return true;
        }
        // Map, List, and SparseArray are all parcelable, with restrictions on their component type
        // that we can't check here.
        if (ClassUtils.isAssignable(clazz, Map.class) ||
            ClassUtils.isAssignable(clazz, List.class) ||
            ClassUtils.isAssignable(clazz, SparseArray.class)) {
            return true;
        }
        // IBinder is parcelable.
        if (ClassUtils.isAssignable(clazz, IBinder.class)) {
            return true;
        }
        // Serializable is parcelable.
        if (ClassUtils.isAssignable(clazz, Serializable.class)) {
            return true;
        }
        return false;
    }

    public static boolean canParcelTypes(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (!canParcelType(clazz, false)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canParcelObject(Object obj) {
        Parcel p = Parcel.obtain();
        try {
            p.writeValue(obj);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            p.recycle();
        }
    }

    private final byte[] data;

    private ParceledPayload(byte[] data) {
        if (localLOGV) {
            Log.v(TAG, "Payload = " + ArrayUtils.toString(data));
        }
        this.data = data;
    }

    public static ParceledPayload create(Object object) {
        Parcel p = Parcel.obtain();
        boolean oldFds = p.pushAllowFds(false);
        try {
            p.writeValue(object);
            return new ParceledPayload(p.marshall());
        } finally {
            p.restoreAllowFds(oldFds);
            p.recycle();
        }
    }

    public static ParceledPayload fromParcel(Parcel p) {
        byte[] data = p.createByteArray();
        if (data == null) {
            // Null data = data's stored in an ashmem region.
            try (ParcelFileDescriptor pfd = p.readFileDescriptor()) {
                FileDescriptor fd = pfd.getFileDescriptor();
                int size = MemoryFile.getSize(fd);
                if (size == -1) {
                    throw new ParcelFormatException("ParceledPayload blob is not ashmem");
                }
                data = new byte[size];
                FileInputStream fis = new FileInputStream(fd);
                FileChannel chan = fis.getChannel();
                MappedByteBuffer mapping = chan.map(FileChannel.MapMode.READ_ONLY, 0, size);
                mapping.get(data);
            } catch (IOException e) {
                Log.e(TAG, "Couldn't unparcel - not an ashmem region?", e);
                ParcelFormatException pfe = new ParcelFormatException("Exception reading blob for ParceledPayload");
                pfe.initCause(e);
                throw pfe;
            }
        }
        return new ParceledPayload(data);
    }

    public Object getValue(ClassLoader loader) {
        Parcel p = Parcel.obtain();
        try {
            p.unmarshall(data, 0, data.length);
            p.setDataPosition(0);
            return p.readValue(loader);
        } finally {
            p.recycle();
        }
    }

    @Override
    public String toString() {
        if (data == null) {
            return "parceled[<null>]";
        } else {
            return String.format("parceled[%d]", data.length);
        }
    }

    private static final int INLINE_SIZE = 32*1024;

    @Override
    public int describeContents() {
        return (data.length >= INLINE_SIZE) ? CONTENTS_FILE_DESCRIPTOR : 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (data.length < INLINE_SIZE) {
            dest.writeByteArray(data);
        } else {
            dest.writeByteArray(null);
            MemoryFile mf = null;
            try {
                mf = new MemoryFile("ParceledPayload", data.length);
                mf.writeBytes(data, 0, 0, data.length);
                FileDescriptor ashmemFd = mf.getFileDescriptor();
                dest.writeFileDescriptor(ashmemFd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (mf != null) {
                    mf.close();
                }
            }
        }
    }

    public static final Parcelable.Creator<ParceledPayload> CREATOR =
            new Parcelable.Creator<ParceledPayload>() {
        @Override
        public ParceledPayload createFromParcel(Parcel source) {
            return ParceledPayload.fromParcel(source);
        }

        @Override
        public ParceledPayload[] newArray(int size) {
            return new ParceledPayload[size];
        }
    };
}

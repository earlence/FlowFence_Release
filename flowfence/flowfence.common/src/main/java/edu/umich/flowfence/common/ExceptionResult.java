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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;
import java.util.concurrent.Callable;

public class ExceptionResult<T> implements Parcelable {
    private final ParceledThrowable exception = new ParceledThrowable();
    private T result = null;
    private ParcelIO<T> io = null;

    public ExceptionResult() {
    }

    public ExceptionResult(T result) {
        setResult(result);
    }

    public ExceptionResult(Throwable throwable) {
        setException(throwable);
    }

    public ExceptionResult(Callable<? extends T> closure) {
        set(closure);
    }

    public final void setException(Throwable throwable) {
        this.exception.set(throwable);
        this.result = null;
    }

    public final void setResult(T result) {
        this.exception.set(null);
        this.result = result;
    }

    public final void set(Callable<? extends T> closure) {
        try {
            setResult(closure.call());
        } catch (Throwable t) {
            setException(t);
        }
    }

    public final void throwChecked() throws Exception {
        exception.throwChecked();
    }

    public final void throwUnchecked() {
        exception.throwUnchecked();
    }

    public final boolean isException() {
        return exception.isError();
    }

    public final Throwable getException() {
        return exception.get();
    }

    public final T getResult() {
        throwUnchecked();
        return result;
    }

    @Override
    public String toString() {
        Throwable e = exception.get();
        if (e != null) {
            return String.format("[exception %s]", e);
        } else {
            return Objects.toString(result);
        }
    }

    //region Extension point for creator
    protected static interface ParcelIO<TResult> {
        public TResult readFromParcel(Parcel source, ClassLoader loader);
        public void writeToParcel(TResult data, Parcel dest, int flags);
    }

    private ParcelIO<T> getIO() {
        synchronized (exception) {
            if (io == null) {
                io = createIO();
            }
            return io;
        }
    }

    protected ParcelIO<T> createIO() {
        // Default implementation overridable by subclasses.
        return new ParcelIO<T>() {
            @Override
            @SuppressWarnings("unchecked")
            public T readFromParcel(Parcel source, ClassLoader loader) {
                return (T)source.readValue(loader);
            }

            @Override
            public void writeToParcel(T data, Parcel dest, int flags) {
                dest.writeValue(data);
            }
        };
    }

    protected static class ParcelableIO<TParcelable extends Parcelable>
    implements ParcelIO<TParcelable> {
        private final Creator<TParcelable> creator;

        public ParcelableIO(Creator<TParcelable> creator) {
            this.creator = creator;
        }

        public TParcelable readFromParcel(Parcel source, ClassLoader loader) {
            if (source.readInt() == 0) {
                return null;
            } else if (creator instanceof ClassLoaderCreator<?>) {
                return ((ClassLoaderCreator<TParcelable>) creator).createFromParcel(source, loader);
            } else {
                return creator.createFromParcel(source);
            }
        }

        @Override
        public void writeToParcel(TParcelable data, Parcel dest, int flags) {
            if (data == null) {
                dest.writeInt(0);
            } else {
                dest.writeInt(1);
                data.writeToParcel(dest, flags);
            }
        }
    }
    //endregion

    //region Parcelable

    @Override
    public int describeContents() {
        return 0;
    }

    public final void readFromParcel(Parcel source) {
        readFromParcel(source, getClass().getClassLoader());
    }

    public final void readFromParcel(Parcel source, ClassLoader loader) {
        exception.readFromParcel(source);
        if (!exception.isError()) {
            result = getIO().readFromParcel(source, loader);
        } else {
            result = null;
        }
    }

    @Override
    public final void writeToParcel(Parcel dest, int flags) {
        exception.writeToParcel(dest, flags);
        if (!exception.isError()) {
            getIO().writeToParcel(result, dest, flags);
        }
    }

    protected static interface Factory<TElem> {
        public TElem newInstance();
        public TElem[] newArray(int size);
    }

    protected static <TSubclass extends ExceptionResult<?>>
    ClassLoaderCreator<TSubclass> makeCreator(final Factory<TSubclass> factory) {
        return new ClassLoaderCreator<TSubclass>() {
            @Override
            public TSubclass createFromParcel(Parcel source, ClassLoader loader) {
                TSubclass r = factory.newInstance();
                r.readFromParcel(source, loader);
                return r;
            }

            @Override
            public TSubclass createFromParcel(Parcel source) {
                TSubclass r = factory.newInstance();
                r.readFromParcel(source);
                return r;
            }

            @Override
            public TSubclass[] newArray(int size) {
                return factory.newArray(size);
            }
        };
    }

    public static final Creator<ExceptionResult<?>> CREATOR = makeCreator(new Factory<ExceptionResult<?>>() {
        @Override
        public ExceptionResult<?> newInstance() {
            return new ExceptionResult<Object>();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public ExceptionResult[] newArray(int size) {
            return new ExceptionResult[size];
        }
    });

    //endregion
}

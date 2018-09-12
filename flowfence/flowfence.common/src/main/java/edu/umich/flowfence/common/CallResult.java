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
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

public final class CallResult implements Parcelable {
	// Parcel format:
	// int32 hasException
	// if hasException:
	//     String descriptor
	//     String message
	// else:
	//     List<IBinder> outParams;

	public static final int RETURN_VALUE = -1;

	private final SparseArray<? extends IBinder> outHandles;
    private final ParceledThrowable throwable;
	
	public CallResult(SparseArray<? extends IBinder> outHandles) {
		this.throwable = new ParceledThrowable();
        this.outHandles = outHandles;
	}
	
	public CallResult(Throwable throwable) {
		this.outHandles = null;
		this.throwable = new ParceledThrowable(throwable);
	}
	
	public CallResult(Parcel p) {
        this.throwable = ParceledThrowable.CREATOR.createFromParcel(p);
		if (!throwable.isError()) {
            int numEntries = p.readInt();
			SparseArray<IBinder> outHandles = new SparseArray<>(numEntries);
            while (numEntries-- > 0) {
                int index = p.readInt();
                IBinder binder = p.readStrongBinder();
                outHandles.append(index, binder);
            }
			this.outHandles = outHandles;
		} else {
            outHandles = null;
        }
	}
	
	public void throwForException() {
		throwable.throwUnchecked();
	}

	public Throwable getThrowable() {
		return throwable.get();
	}
	
	public SparseArray<IBinder> getOutputs() {
		throwForException();
		final int size = outHandles.size();
		final SparseArray<IBinder> rv = new SparseArray<>(size);
		for (int i = 0; i < size; i++) {
			rv.append(outHandles.keyAt(i), outHandles.valueAt(i));
		}
		return rv;
	}

    public IBinder getOutput(int index) {
        throwForException();
        return outHandles.get(index, null);
    }
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
        throwable.writeToParcel(dest, flags);
		if (!throwable.isError()) {
            int numEntries = outHandles.size();
			dest.writeInt(numEntries);
            for (int i = 0; i < numEntries; i++) {
                dest.writeInt(outHandles.keyAt(i));
                dest.writeStrongBinder(outHandles.valueAt(i));
            }
		}
	}
	
	public static final Parcelable.Creator<CallResult> CREATOR = new Parcelable.Creator<CallResult>() {
		@Override
		public CallResult createFromParcel(Parcel source) {
			return new CallResult(source);
		}

		@Override
		public CallResult[] newArray(int size) {
			return new CallResult[size];
		}
	};
}

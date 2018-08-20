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

package edu.umich.flowfence.internal;

import android.os.Parcel;

import edu.umich.flowfence.common.ExceptionResult;
import edu.umich.flowfence.internal.IResolvedSoda;

public final class ResolvedSodaExceptionResult extends ExceptionResult<IResolvedSoda> {
    @Override
    protected final ParcelIO<IResolvedSoda> createIO() {
        return new ParcelIO<IResolvedSoda>() {
            @Override
            public IResolvedSoda readFromParcel(Parcel source, ClassLoader loader) {
                return IResolvedSoda.Stub.asInterface(source.readStrongBinder());
            }

            @Override
            public void writeToParcel(IResolvedSoda data, Parcel dest, int flags) {
                dest.writeStrongInterface(data);
            }
        };
    }

    public static final Creator<ResolvedSodaExceptionResult> CREATOR =
            makeCreator(new Factory<ResolvedSodaExceptionResult>() {
        @Override
        public ResolvedSodaExceptionResult newInstance() {
            return new ResolvedSodaExceptionResult();
        }

        @Override
        public ResolvedSodaExceptionResult[] newArray(int size) {
            return new ResolvedSodaExceptionResult[size];
        }
    });
}

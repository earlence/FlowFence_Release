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

public final class ResolvedQMExceptionResult extends ExceptionResult<IResolvedQM> {
    @Override
    protected final ParcelIO<IResolvedQM> createIO() {
        return new ParcelIO<IResolvedQM>() {
            @Override
            public IResolvedQM readFromParcel(Parcel source, ClassLoader loader) {
                return IResolvedQM.Stub.asInterface(source.readStrongBinder());
            }

            @Override
            public void writeToParcel(IResolvedQM data, Parcel dest, int flags) {
                dest.writeStrongInterface(data);
            }
        };
    }

    public static final Creator<ResolvedQMExceptionResult> CREATOR =
            makeCreator(new Factory<ResolvedQMExceptionResult>() {
        @Override
        public ResolvedQMExceptionResult newInstance() {
            return new ResolvedQMExceptionResult();
        }

        @Override
        public ResolvedQMExceptionResult[] newArray(int size) {
            return new ResolvedQMExceptionResult[size];
        }
    });
}

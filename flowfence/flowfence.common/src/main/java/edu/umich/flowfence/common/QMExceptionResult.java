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

public final class QMExceptionResult extends ExceptionResult<IQM> {
    @Override
    protected ParcelIO<IQM> createIO() {
        return new ParcelIO<IQM>() {
            @Override
            public IQM readFromParcel(Parcel source, ClassLoader loader) {
                return IQM.Stub.asInterface(source.readStrongBinder());
            }

            @Override
            public void writeToParcel(IQM data, Parcel dest, int flags) {
                dest.writeStrongInterface(data);
            }
        };
    }

    public static final Creator<QMExceptionResult> CREATOR = makeCreator(new Factory<QMExceptionResult>() {
        @Override
        public QMExceptionResult newInstance() {
            return new QMExceptionResult();
        }

        @Override
        public QMExceptionResult[] newArray(int size) {
            return new QMExceptionResult[size];
        }
    });
}

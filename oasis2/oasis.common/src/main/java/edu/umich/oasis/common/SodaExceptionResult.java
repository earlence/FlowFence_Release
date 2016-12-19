package edu.umich.oasis.common;

import android.os.Parcel;

/**
 * Created by jpaupore on 1/27/15.
 */
public final class SodaExceptionResult extends ExceptionResult<ISoda> {
    @Override
    protected ParcelIO<ISoda> createIO() {
        return new ParcelIO<ISoda>() {
            @Override
            public ISoda readFromParcel(Parcel source, ClassLoader loader) {
                return ISoda.Stub.asInterface(source.readStrongBinder());
            }

            @Override
            public void writeToParcel(ISoda data, Parcel dest, int flags) {
                dest.writeStrongInterface(data);
            }
        };
    }

    public static final Creator<SodaExceptionResult> CREATOR = makeCreator(new Factory<SodaExceptionResult>() {
        @Override
        public SodaExceptionResult newInstance() {
            return new SodaExceptionResult();
        }

        @Override
        public SodaExceptionResult[] newArray(int size) {
            return new SodaExceptionResult[size];
        }
    });
}

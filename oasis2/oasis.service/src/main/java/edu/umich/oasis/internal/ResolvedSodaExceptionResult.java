package edu.umich.oasis.internal;

import android.os.Parcel;

import edu.umich.oasis.common.ExceptionResult;
import edu.umich.oasis.internal.IResolvedSoda;

/**
 * Created by jpaupore on 1/28/15.
 */
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

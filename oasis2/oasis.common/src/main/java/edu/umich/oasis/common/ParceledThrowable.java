package edu.umich.oasis.common;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Created by jpaupore on 1/27/15.
 */
public final class ParceledThrowable implements Parcelable {

    public static final class MarshalFailedException extends OASISException {
        public static final long serialVersionUID = 1L;
        private final String typeName;
        private final byte[] payload;

        public final String getTypeName() {
            return typeName;
        }

        public final byte[] getPayload() {
            return payload.clone();
        }

        public MarshalFailedException(String typeName, Throwable cause, byte[] payload) {
            super("Could not deserialize " + typeName, cause);
            this.typeName = typeName;
            this.payload = payload;
        }
    }

    private Throwable throwable;

    public ParceledThrowable() {
        this((Exception)null);
    }

    public ParceledThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public ParceledThrowable(Parcel source) {
        readFromParcel(source);
    }

    public Throwable get() {
        return throwable;
    }

    public void set(Throwable throwable) {
        this.throwable = throwable;
    }

    public boolean isError() {
        return (throwable != null);
    }

    public void throwChecked() throws Exception {
        throwUnchecked();
    }

    public void throwUnchecked() {
        if (throwable != null) {
            throwUnchecked(throwable);
        }
    }

    public static void throwUnchecked(Throwable t) {
        ParceledThrowable.<Error>throwSneaky(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwSneaky(Throwable t) throws T {
        throw (T) t;
    }

    //region Parcelable
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeThrowableToParcel(this.throwable, dest, flags);
    }

    private static void writeThrowableToParcel(Throwable throwable, Parcel dest, int flags) {
        if (throwable == null) {
            dest.writeString(null);
            return;
        }

        // Transparently flatten ExceptionMarshalFailedException.
        if (throwable instanceof MarshalFailedException) {
            MarshalFailedException de = (MarshalFailedException)throwable;
            dest.writeString(de.typeName);
            dest.writeByteArray(de.payload);
            // Recurse into suppressed exceptions.
            Throwable[] suppressed = de.getSuppressed();
            dest.writeInt(suppressed.length);
            for (Throwable t : suppressed) {
                writeThrowableToParcel(t, dest, flags);
            }
            return;
        }

        String name = throwable.getClass().getName();
        dest.writeString(name);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(throwable);
            dest.writeByteArray(baos.toByteArray());
            dest.writeInt(0); // no more suppressed exceptions; Serializable handled it.
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static Throwable readThrowableFromParcel(Parcel source) {
        String name = source.readString();
        if (name == null) {
            return null;
        }

        byte[] payload = source.createByteArray();
        Throwable result;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            result = (Throwable)ois.readObject();
        } catch (Exception e) {
            result = new MarshalFailedException(name, e, payload);
        }

        // Handle suppressed.
        int numSuppressed = source.readInt();
        while (numSuppressed-- > 0) {
            Throwable suppressed = readThrowableFromParcel(source);
            if (suppressed != null) {
                result.addSuppressed(suppressed);
            }
        }

        return result;
    }

    public void readFromParcel(Parcel source) {
        this.throwable = readThrowableFromParcel(source);
    }

    public static final Parcelable.Creator<ParceledThrowable> CREATOR = new Parcelable.Creator<ParceledThrowable>() {
        @Override
        public ParceledThrowable createFromParcel(Parcel source) {
            return new ParceledThrowable(source);
        }

        @Override
        public ParceledThrowable[] newArray(int size) {
            return new ParceledThrowable[size];
        }
    };
    //endregion
}

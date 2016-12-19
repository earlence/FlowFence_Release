package edu.umich.oasis.common;

/**
 * Created by jpaupore on 2/2/15.
 */
public class ParceledPayloadExceptionResult extends ExceptionResult<ParceledPayload> {
    @Override
    protected ParcelIO<ParceledPayload> createIO() {
        return new ParcelableIO<>(ParceledPayload.CREATOR);
    }

    public static final Creator<ParceledPayloadExceptionResult> CREATOR =
        makeCreator(new Factory<ParceledPayloadExceptionResult>() {
            @Override
            public ParceledPayloadExceptionResult newInstance() {
                return new ParceledPayloadExceptionResult();
            }

            @Override
            public ParceledPayloadExceptionResult[] newArray(int size) {
                return new ParceledPayloadExceptionResult[size];
            }
        });
}

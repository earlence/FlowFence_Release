package edu.umich.oasis.common;

/**
 * Created by jpaupore on 1/25/15.
 */
public class WrappedRemoteException extends OASISException {
    private static final long serialVersionUID = 1L;
    public WrappedRemoteException() {
    }

    public WrappedRemoteException(String detailMessage) {
        super(detailMessage);
    }

    public WrappedRemoteException(Throwable throwable) {
        super(throwable);
    }

    public WrappedRemoteException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}

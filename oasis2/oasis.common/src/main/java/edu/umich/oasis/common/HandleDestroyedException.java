package edu.umich.oasis.common;

/**
 * Created by jpaupore on 1/29/15.
 */
public class HandleDestroyedException extends OASISException {
    private static final long serialVersionUID = 1L;
    public HandleDestroyedException() {
        super();
    }

    public HandleDestroyedException(String detailMessage) {
        super(detailMessage);
    }

    public HandleDestroyedException(Throwable throwable) {
        super(throwable);
    }

    public HandleDestroyedException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}

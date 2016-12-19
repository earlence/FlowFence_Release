package edu.umich.oasis.policy;

import edu.umich.oasis.common.OASISException;

/**
 * Created by jpaupore on 1/13/16.
 */
public class PolicyParseException extends OASISException {
    private static final long serialVersionUID = 1L;

    public PolicyParseException() {
    }

    public PolicyParseException(String detailMessage) {
        super(detailMessage);
    }

    public PolicyParseException(Throwable throwable) {
        super(throwable);
    }

    public PolicyParseException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}

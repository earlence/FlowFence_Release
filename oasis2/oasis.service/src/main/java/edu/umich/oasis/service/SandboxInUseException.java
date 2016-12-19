package edu.umich.oasis.service;

import edu.umich.oasis.common.OASISException;

/**
 * Created by jpaupore on 1/28/16.
 */
public class SandboxInUseException extends OASISException {
    private static final long serialVersionUID = 0x5BC0111DEDL;
    public SandboxInUseException() {
    }

    public SandboxInUseException(String detailMessage) {
        super(detailMessage);
    }

    public SandboxInUseException(Throwable throwable) {
        super(throwable);
    }

    public SandboxInUseException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}

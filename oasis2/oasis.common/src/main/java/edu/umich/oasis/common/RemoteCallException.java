package edu.umich.oasis.common;

public class RemoteCallException extends OASISException {

	private static final long serialVersionUID = -8373310407197822685L;

	public RemoteCallException() {
	}

	public RemoteCallException(String detailMessage) {
		super(detailMessage);
	}

	public RemoteCallException(Throwable throwable) {
		super(throwable);
	}

	public RemoteCallException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}

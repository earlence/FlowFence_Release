package edu.umich.oasis.common;

public class OASISException extends RuntimeException {

	private static final long serialVersionUID = 2696825715685925114L;

	public OASISException() {
	}

	public OASISException(String detailMessage) {
		super(detailMessage);
	}

	public OASISException(Throwable throwable) {
		super(throwable);
	}

	public OASISException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}

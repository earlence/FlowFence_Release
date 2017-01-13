/*
 * Copyright (C) 2017 The Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

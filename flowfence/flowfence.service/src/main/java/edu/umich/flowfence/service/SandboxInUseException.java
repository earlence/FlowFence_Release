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

package edu.umich.flowfence.service;

import edu.umich.flowfence.common.OASISException;

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

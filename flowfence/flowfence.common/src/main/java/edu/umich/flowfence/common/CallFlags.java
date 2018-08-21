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

package edu.umich.flowfence.common;

public final class CallFlags {
    // Flags
    public static final int CALL_SYNC           = 0x00000000;
    public static final int CALL_ASYNC          = 0x80000000;
    public static final int FORCE_SYNC_ASYNC    = 0x40000000;

    public static final int FORCE_SYNC          = CALL_SYNC | FORCE_SYNC_ASYNC;
    public static final int FORCE_ASYNC         = CALL_ASYNC | FORCE_SYNC_ASYNC;

    public static final int NO_RETURN_VALUE     = 0x20000000;
    public static final int FILTER_EXCEPTIONS   = 0x10000000; // TODO
    public static final int OVERRIDE_SANDBOX    = 0x08000000;

    public static final int SANDBOX_NUM_MASK    = (Integer.highestOneBit(FlowfenceConstants.NUM_SANDBOXES-1) << 1) - 1;
}

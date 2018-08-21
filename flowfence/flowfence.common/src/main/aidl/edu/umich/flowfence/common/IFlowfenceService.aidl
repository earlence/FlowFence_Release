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

// IFlowfenceService.aidl
package edu.umich.flowfence.common;

// Declare any non-default types here with import statements
import edu.umich.flowfence.common.ExceptionResult;
import edu.umich.flowfence.common.QMDescriptor;
import edu.umich.flowfence.common.QMExceptionResult;
import edu.umich.flowfence.common.QMDetails;
import edu.umich.flowfence.common.IQM;
import android.content.ComponentName;
import android.os.Debug;

interface IFlowfenceService {

    //client app facing API
    QMExceptionResult resolveQM(in QMDescriptor descriptor, int flags, inout QMDetails details);

    int setSandboxCount(int count);
    int setMaxIdleCount(int count);
    int setMinHotSpare(int count);
    int setMaxHotSpare(int count);
    void restartSandbox(int sandboxId);

    ExceptionResult subscribeEventChannel(in ComponentName channel, in QMDescriptor descriptor);
    ExceptionResult unsubscribeEventChannel(in ComponentName channel, in QMDescriptor descriptor);
    ExceptionResult subscribeEventChannelHandle(in ComponentName channel, in IQM qmRef);
    ExceptionResult unsubscribeEventChannelHandle(in ComponentName channel, in IQM qmRef);

    // Debugging and experiment stuff. Requires holding DEBUG_FLOWFENCE_SERVICE permission.
    void forceGarbageCollection();
    Debug.MemoryInfo dumpMemoryInfo(out List<Debug.MemoryInfo> sandboxInfo);
}

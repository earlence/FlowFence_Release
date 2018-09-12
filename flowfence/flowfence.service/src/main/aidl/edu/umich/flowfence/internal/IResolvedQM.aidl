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

// IResolvedQM.aidl
package edu.umich.flowfence.internal;

// Declare any non-default types here with import statements
import android.os.Bundle;
import android.os.Debug;
import android.content.ComponentName;
import edu.umich.flowfence.common.ParamInfo;
import edu.umich.flowfence.common.CallParam;
import edu.umich.flowfence.common.CallResult;
import edu.umich.flowfence.common.QMDescriptor;
import edu.umich.flowfence.common.QMDetails;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.internal.IQMCallback;

interface IResolvedQM {
    void getDetails(inout QMDetails details);

    oneway void call(in int flags,
                     in IQMCallback callback,
                     in List<CallParam> params);
}
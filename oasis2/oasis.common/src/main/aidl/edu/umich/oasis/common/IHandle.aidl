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

import android.content.ComponentName;
import edu.umich.oasis.common.IHandleDebug;
import edu.umich.oasis.common.ParamInfo;
import edu.umich.oasis.common.ParceledPayloadExceptionResult;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.TaintSet;

interface IHandle {
	void release();
	IHandle withTaint(in TaintSet newTaints);

	SodaDescriptor getSodaDescriptor();
	int getParamIndex();
	ParamInfo getParamInfo();

	IHandleDebug getDebug();

	// Returns null if declassification is not allowed.
	ParceledPayloadExceptionResult tryDeclassify(boolean mergeTaints);
	boolean isComplete();
	boolean tryWaitForComplete();
}
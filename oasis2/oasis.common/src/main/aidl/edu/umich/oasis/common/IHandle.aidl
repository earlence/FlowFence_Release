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
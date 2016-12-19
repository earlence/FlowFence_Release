package edu.umich.oasis.common;

import edu.umich.oasis.common.CallParam;
import edu.umich.oasis.common.CallResult;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.SodaDetails;
import edu.umich.oasis.common.ParamInfo;
import edu.umich.oasis.common.TaintSet;

interface ISoda {
	SodaDescriptor getDescriptor();
	String getResultType();
	List<ParamInfo> getParamInfo();
	TaintSet getRequiredTaints();
	TaintSet getOptionalTaints();

	void getDetails(inout SodaDetails details);
	
	CallResult call(in int flags, in List<CallParam> params, in TaintSet extraTaint);
}
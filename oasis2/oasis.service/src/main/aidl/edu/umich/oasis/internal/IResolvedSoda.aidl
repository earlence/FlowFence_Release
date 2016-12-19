// IResolvedSoda.aidl
package edu.umich.oasis.internal;

// Declare any non-default types here with import statements
import android.os.Bundle;
import android.os.Debug;
import android.content.ComponentName;
import edu.umich.oasis.common.ParamInfo;
import edu.umich.oasis.common.CallParam;
import edu.umich.oasis.common.CallResult;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.SodaDetails;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.internal.ISodaCallback;

interface IResolvedSoda {
    void getDetails(inout SodaDetails details);

    oneway void call(in int flags,
                     in ISodaCallback callback,
                     in List<CallParam> params);
}
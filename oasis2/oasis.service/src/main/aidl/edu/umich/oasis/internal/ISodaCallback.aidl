// ISodaCallback.aidl
package edu.umich.oasis.internal;

// Declare any non-default types here with import statements
import edu.umich.oasis.common.CallResult;

interface ISodaCallback {
    oneway void onResult(in CallResult result);
}

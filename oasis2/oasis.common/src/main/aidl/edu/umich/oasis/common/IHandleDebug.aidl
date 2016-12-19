// IHandleDebug.aidl
package edu.umich.oasis.common;

// Declare any non-default types here with import statements
import edu.umich.oasis.common.ParceledPayloadExceptionResult;
import edu.umich.oasis.common.TaintSet;

interface IHandleDebug {
    boolean isComplete();
    void waitForComplete();
    ParceledPayloadExceptionResult getData();
    String getClassName();
    TaintSet getTaints();
}

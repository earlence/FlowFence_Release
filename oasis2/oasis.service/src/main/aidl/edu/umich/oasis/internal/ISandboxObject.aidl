// ISandboxObject.aidl
package edu.umich.oasis.internal;

// Declare any non-default types here with import statements
import edu.umich.oasis.internal.IResolvedSoda;
import edu.umich.oasis.common.ParceledPayloadExceptionResult;

interface ISandboxObject {
    String getDeclaredClassName();
    String getActualClassName();
    IResolvedSoda getCreator();

    ParceledPayloadExceptionResult marshalOut();
    void destroy();
    boolean isDestroyed();
}

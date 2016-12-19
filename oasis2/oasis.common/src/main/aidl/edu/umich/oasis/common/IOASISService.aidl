// IOASISService.aidl
package edu.umich.oasis.common;

// Declare any non-default types here with import statements
import edu.umich.oasis.common.ExceptionResult;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.SodaExceptionResult;
import edu.umich.oasis.common.SodaDetails;
import edu.umich.oasis.common.ISoda;
import android.content.ComponentName;
import android.os.Debug;

interface IOASISService {

    //client app facing API
    SodaExceptionResult resolveSODA(in SodaDescriptor descriptor, int flags, inout SodaDetails details);

    int setSandboxCount(int count);
    int setMaxIdleCount(int count);
    int setMinHotSpare(int count);
    int setMaxHotSpare(int count);
    void restartSandbox(int sandboxId);

    ExceptionResult subscribeEventChannel(in ComponentName channel, in SodaDescriptor descriptor);
    ExceptionResult unsubscribeEventChannel(in ComponentName channel, in SodaDescriptor descriptor);
    ExceptionResult subscribeEventChannelHandle(in ComponentName channel, in ISoda sodaRef);
    ExceptionResult unsubscribeEventChannelHandle(in ComponentName channel, in ISoda sodaRef);

    // Debugging and experiment stuff. Requires holding DEBUG_OASIS_SERVICE permission.
    void forceGarbageCollection();
    Debug.MemoryInfo dumpMemoryInfo(out List<Debug.MemoryInfo> sandboxInfo);
}

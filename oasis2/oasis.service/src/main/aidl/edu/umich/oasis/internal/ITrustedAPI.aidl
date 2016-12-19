// ISandboxCallout.aidl
package edu.umich.oasis.internal;

// Declare any non-default types here with import statements
import edu.umich.oasis.kvs.IRemoteSharedPrefs;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.common.smartthings.SmartDevice;
import edu.umich.oasis.events.IEventChannelSender;
import android.content.ComponentName;
import java.util.List;

interface ITrustedAPI {
    void taintSelf(in TaintSet taint);
    TaintSet removeTaints(in TaintSet taints);

    IRemoteSharedPrefs openSharedPrefs(String packageName, String storeName, int mode);
    void showToast(CharSequence text, int duration);

    String sendPush(String title, String body);

    //SmartThings Bridge API
    List<SmartDevice> getSwitches();
    void switchOp(String op, String switchId);

    IEventChannelSender getEventChannel(in ComponentName channelName);
}

// IEventChannelSender.aidl
package edu.umich.oasis.events;

// Declare any non-default types here with import statements
import edu.umich.oasis.common.ParceledPayload;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.events.IEventChannelReceiver;
import android.content.ComponentName;

interface IEventChannelSender {
    ComponentName getChannelName();
    void fire(in List<ParceledPayload> parceledArgs, in TaintSet extraTaint);
    void registerTransientReceiver(in IEventChannelReceiver receiver);
    void deregisterTransientReceiver(in IEventChannelReceiver receiver);
}

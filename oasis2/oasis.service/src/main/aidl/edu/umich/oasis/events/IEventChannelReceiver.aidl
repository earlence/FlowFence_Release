// IEventChannelReceiver.aidl
package edu.umich.oasis.events;

// Declare any non-default types here with import statements
import edu.umich.oasis.common.ParceledPayload;

interface IEventChannelReceiver {
    void onEvent(in List<ParceledPayload> payloads);
}

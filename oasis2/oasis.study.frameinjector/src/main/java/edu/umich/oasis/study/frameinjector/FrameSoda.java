package edu.umich.oasis.study.frameinjector;

import android.content.ComponentName;

import edu.umich.oasis.common.IEventChannelAPI;
import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.TaintSet;

/**
 * Created by earlence on 2/9/16.
 */
public class FrameSoda
{
    //private static final String TAG = "Latency";
    //private static final boolean DEBUG_LATENCY = Log.isLoggable(TAG, Log.VERBOSE);

    private static final String TAINT_TAG = "edu.umich.oasis.study.frameinjector/camFrameTaint";
    private static final String CHANNEL_NAME = "edu.umich.oasis.study.frameinjector/camFrameChannel";

    private FrameSoda()
    {
    }

    private static final TaintSet CAMERA_TAINT = new TaintSet.Builder().addTaint(TAINT_TAG).build();
    private static final ComponentName CAMERA_CHANNEL = ComponentName.unflattenFromString(CHANNEL_NAME);

    public static void newFrame(byte [] data, int width, int height, long deliveryTime)
    {
        if(data != null)
        {
            //Log.i(TAG, "frame len: " + data.length);
            /*if (DEBUG_LATENCY) {
                Log.v(TAG, String.format("Latency to frame injector: %d ns",
                        SystemClock.elapsedRealtimeNanos() - deliveryTime));
            }*/

            //fire an event to any listening SODAs
            IEventChannelAPI eventApi = (IEventChannelAPI) OASISContext.getInstance().getTrustedAPI("event");
            eventApi.fireEvent(CAMERA_TAINT, CAMERA_CHANNEL, data, width, height, deliveryTime);
        }
    }
}

/*
 * Copyright (C) 2017 The Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

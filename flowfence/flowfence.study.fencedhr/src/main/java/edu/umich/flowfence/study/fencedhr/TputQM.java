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

package edu.umich.flowfence.study.fencedhr;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import edu.umich.flowfence.common.FlowfenceContext;

public class TputQM
{
    private static final String TAG = "TputQM";

    private TputQM()
    {
    }

    //this will be called by HRService every second
    //the last time it is called, it will output the average fps
    //over the experimentLength (currently 120 seconds)
    public static void poll()
    {
        SharedPreferences myprefs = FlowfenceContext.getInstance().getSharedPreferences(HRQM.TPUTKV, 0);
        int counter = myprefs.getInt("counter", -1);

        if(counter != -1)
        {
            long now = SystemClock.uptimeMillis();
            long prev = myprefs.getLong("ts", -1);

            if(prev != -1)
            {
                double duration = (((double) (now - prev)) / 1000.0); //seconds
                double fps = ((double) (counter)) / duration;

                Log.i(TAG, "fps: " + fps);
                //Log.i(TAG, "duration: " + duration);

                //reset counter
                SharedPreferences.Editor edit = myprefs.edit();
                edit.putInt("counter", 0);
                edit.apply();

            }
            else
                Log.e(TAG, "prev == -1");
        }
        else
            Log.e(TAG, "counter == -1");
    }
}

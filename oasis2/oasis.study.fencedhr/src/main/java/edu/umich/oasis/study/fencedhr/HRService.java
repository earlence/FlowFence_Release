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

package edu.umich.oasis.study.fencedhr;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import edu.umich.oasis.client.OASISConnection;
import edu.umich.oasis.client.Soda;
import edu.umich.oasis.common.SodaDescriptor;

public class HRService extends Service {

    private static final String TAG = "HRService";
    OASISConnection oconn = null;
    Soda.S4<byte [], Integer, Integer, Long, Void> newFrameStatic = null;
    Soda.S0<Void> pollStatic = null;


    Timer thrTimer;
    TputPoller tputPoller;
    int interval = 1; // in seconds

    public HRService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        connectToOASIS();



        // If we get killed, after returning from here, restart
        return START_STICKY;
    }


    private void onOASISConnect(OASISConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to OASIS", Toast.LENGTH_SHORT);
        t.show();

        resolve();
        setupListener();

        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction("stopPoll");
        ifilter.addAction("startPoll");
        getApplication().registerReceiver(new MyRecv(), ifilter);

        //TODO unregister the BR
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {
                newFrameStatic = oconn.resolveStatic(void.class, HRSoda.class, "newFrame", byte [].class, int.class, int.class, long.class);
                pollStatic = oconn.resolveStatic(void.class, TputSoda.class, "poll");
            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    public void connectToOASIS()
    {
        Log.i(TAG, "Binding to OASIS...");
        OASISConnection.bind(this, new OASISConnection.Callback() {
            @Override
            public void onConnect(OASISConnection conn) throws Exception {
                Log.i(TAG, "Bound to OASIS");
                onOASISConnect(conn);
            }
        });
    }

    public void setupListener()
    {
        SodaDescriptor sd = newFrameStatic.getDescriptor();
        ComponentName cn = new ComponentName("edu.umich.oasis.study.frameinjector", "camFrameChannel");

        try {
            oconn.getRawInterface().subscribeEventChannel(cn, sd);
        } catch (Exception e)
        {
            Log.e(TAG, "error subscribeEventChannel: " + e);
        }
    }

    class TputPoller extends TimerTask
    {
         public void run()
         {
             //execute the TputSoda
             try {
                 pollStatic.call();
             } catch(Exception e)
             {
                 Log.e(TAG, "error pollStatic: " + e);
             }

         }
    }

    public class MyRecv extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals("stopPoll"))
            {
                thrTimer.cancel();
            }
            else if(intent.getAction().equals("startPoll"))
            {
                thrTimer = new Timer();
                tputPoller = new TputPoller();
                thrTimer.schedule(tputPoller, 0, interval * 1000); //once a second
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}

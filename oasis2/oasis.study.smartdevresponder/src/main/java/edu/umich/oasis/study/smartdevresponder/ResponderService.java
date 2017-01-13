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
package edu.umich.oasis.study.smartdevresponder;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import edu.umich.oasis.client.OASISConnection;
import edu.umich.oasis.client.Sealed;
import edu.umich.oasis.client.Soda;
import edu.umich.oasis.common.SodaDescriptor;

public class ResponderService extends Service {
    private static final String TAG = "ResponderService";

    OASISConnection oconn = null;
    Soda.S0<Void> pollPresence = null; //takes a "this", and returns a void
    Soda.S0<ResponderSoda> ctor = null; //returns an instance of the class
    Sealed<ResponderSoda> sodaInst = null;

    TimerTask timerTask;
    Timer timer;

    public ResponderService()
    {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        connectToOASIS();

        // If we get killed, after returning from here, restart
        return START_STICKY;
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

    public void resolve()
    {
        if(oconn != null)
        {
            try {

                //ctor = oconn.resolveConstructor(ResponderSoda.class);
                //sodaInst = ctor.call();

                pollPresence = oconn.resolveStatic(void.class, ResponderSoda.class, "pollPresenceAndCompute");

            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    private void onOASISConnect(OASISConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to OASIS", Toast.LENGTH_SHORT);
        t.show();



        resolve();

        //fire up a timer that will execute pollPresence frequently
        //This is stop-gap until we get pubsub on the KV store
        //initTTask();
        //schedule();

        //setup ourselves so that we are called when the presence KV store is updated
        setupListener();
    }

    void setupListener()
    {
        SodaDescriptor sd = pollPresence.getDescriptor();
        ComponentName cn = new ComponentName("edu.umich.oasis.study.presencebasedcontrol", "presenceUpdateChannel");

        try {
            oconn.getRawInterface().subscribeEventChannel(cn, sd);
        } catch (Exception e)
        {
            Log.e(TAG, "error subscribeEventChannel: " + e);
        }
    }

    /*void schedule()
    {
        timer = new Timer();
        timer.schedule(timerTask, 1000, 1000);

        //TODO timer.cancel
    }

    void initTTask()
    {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                task();
            }
        };
    }

    void task()
    {
        if(pollPresence != null)
        {
            //sandbox allocation work-around
            //try {
            //    oconn.getRawInterface().setNextSandbox(2);
            //} catch(Exception e)
            //{
            //    Log.e(TAG, "error setNextSandbox(2)");
            //}

            try {
                Log.i(TAG, "attempting SODA call");
                pollPresence.arg(sodaInst).call();
            } catch(Exception e)
            {
                Log.i(TAG, "error: " + e);

            }
        }
    }*/

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

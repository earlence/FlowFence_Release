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
package edu.umich.flowfence.study.smartdevresponder;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.Sealed;
import edu.umich.flowfence.client.QuarentineModule;
import edu.umich.flowfence.common.QMDescriptor;

public class ResponderService extends Service {
    private static final String TAG = "ResponderService";

    FlowfenceConnection oconn = null;
    QuarentineModule.S0<Void> pollPresence = null; //takes a "this", and returns a void
    QuarentineModule.S0<ResponderQM> ctor = null; //returns an instance of the class
    Sealed<ResponderQM> qmInst = null;

    TimerTask timerTask;
    Timer timer;

    public ResponderService()
    {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        connectToFlowfence();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    public void connectToFlowfence()
    {
        Log.i(TAG, "Binding to Flowfence...");
        FlowfenceConnection.bind(this, new FlowfenceConnection.Callback() {
            @Override
            public void onConnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Bound to Flowfence");
                onFlowfenceConnect(conn);
            }
        });
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {

                //ctor = oconn.resolveConstructor(ResponderQM.class);
                //qmInst = ctor.call();

                pollPresence = oconn.resolveStatic(void.class, ResponderQM.class, "pollPresenceAndCompute");

            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    private void onFlowfenceConnect(FlowfenceConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to Flowfence", Toast.LENGTH_SHORT);
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
        QMDescriptor sd = pollPresence.getDescriptor();
        ComponentName cn = new ComponentName("edu.umich.flowfence.study.presencebasedcontrol", "presenceUpdateChannel");

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
                Log.i(TAG, "attempting QM call");
                pollPresence.arg(qmInst).call();
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

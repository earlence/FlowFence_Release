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

package edu.umich.flowfence.study.presencebasedcontrol;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.QuarentineModule;

public class PresenceInjector extends Service {

    private static final String TAG = "PresenceInjector";

    private static final String TIMING_TAG_START = "timeStart";
    private static final String TIMING_TAG_END = "timeEnd";
    private static final boolean DEBUG_TIME = true;

    private static String FIREBASE_URL = "https://blinding-inferno-7958.firebaseio.com/";
    private static String LOC_KEY = "location";
    Firebase firebaseRef;

    FlowfenceConnection oconn = null;
    QuarentineModule.S1<String, Void> putLocStatic = null;

    public PresenceInjector()
    {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        connectToFlowfence();
        init();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    public void connectToFlowfence()
    {
        Log.i(TAG, "Binding to FlowFence...");
        FlowfenceConnection.bind(this, new FlowfenceConnection.Callback() {
            @Override
            public void onConnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Bound to FlowFence");
                onFlowfenceConnect(conn);
            }
        });
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {
                putLocStatic = oconn.resolveStatic(void.class, PresenceQM.class, "putLoc", String.class);
            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    private void onFlowfenceConnect(FlowfenceConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to FlowFence", Toast.LENGTH_SHORT);
        t.show();

        resolve();
    }

    private void init()
    {
        Firebase.setAndroidContext(this);
        firebaseRef = new Firebase(FIREBASE_URL);

        firebaseRef.authWithPassword("test_rx@gmail.com", "test123", new Firebase.AuthResultHandler()
        {
            @Override
            public void onAuthenticated(AuthData authData)
            {
                rx();
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError)
            {
                Log.i(TAG, "Firebase Auth Error: " + firebaseError);
            }
        });


    }

    private void rx()
    {
        firebaseRef.child(LOC_KEY).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String loc = (String) dataSnapshot.getValue();
                Log.i(TAG, "updating loc to: " + loc);

                //update the FlowFence KV store with new loc
                updateFlowFenceKV(loc);


            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    private void updateFlowFenceKV(String newloc)
    {
        if(putLocStatic != null)
        {
            //sandbox allocation workaround
            /*try {
                oconn.getRawInterface().setNextSandbox(1);
            } catch(Exception e)
            {
                Log.e(TAG, "error setNextSandbox(1)");
            }*/

            try {

                //TimeStamp when the KV is updated
                if(DEBUG_TIME)
                {
                    Log.i(TIMING_TAG_START, "" + SystemClock.uptimeMillis());
                }

                putLocStatic.arg(newloc).call();
            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
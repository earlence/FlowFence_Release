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

package edu.umich.flowfence.study.fencedfrdc;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.QuarentineModule;
import edu.umich.flowfence.common.QMDescriptor;

public class FaceOpsService extends Service {
    private static final String TAG = "FaceOpsService";
    FlowfenceConnection oconn = null;

    QuarentineModule.S4<Integer, Integer, Bitmap, Long, Void> bmpRxStatic = null;

    public FaceOpsService()
    {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        connectToFlowfence();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    private void onFlowfenceConnect(FlowfenceConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to FlowFence", Toast.LENGTH_SHORT);
        t.show();

        resolve();
        setupListener();
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {
                bmpRxStatic = oconn.resolveStatic(void.class, FRDCQM.class, "bmpRx", int.class, int.class, Bitmap.class, long.class);

            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
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

    public void setupListener()
    {
        QMDescriptor sd = bmpRxStatic.getDescriptor();
        ComponentName cn = new ComponentName("edu.umich.flowfence.study.caminjector", "cameraBMPChannel");

        try {
            oconn.getRawInterface().subscribeEventChannel(cn, sd);
        } catch (Exception e)
        {
            Log.e(TAG, "error subscribeEventChannel: " + e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}

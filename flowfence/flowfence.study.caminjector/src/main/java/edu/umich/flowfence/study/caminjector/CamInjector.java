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

package edu.umich.flowfence.study.caminjector;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.QuarentineModule;

// This service pushes a bitmap into CamQM.
// CamQM then fires an event with that bitmap
public class CamInjector extends Service {
    private static final String TAG = "CamInjector";

    private static final String TIMING_TAG_START = "timeStart";
    private static final String TIMING_TAG_END = "timeEnd";
    private static final boolean DEBUG_TIME = true;

    FlowfenceConnection oconn = null;

    QuarentineModule.S4<Bitmap, Integer, Integer, Long, Void> sendBMPStatic = null;

    public DataSource ds = null;

    public final int numImages = 5;

    private final IBinder mBinder = new CamBinder();

    public CamInjector()
    {
    }

    public class CamBinder extends Binder {
        CamInjector getService()
        {
            return CamInjector.this;
        }
    }

    public void init()
    {
        connectToQM();
        ds = new DataSource(numImages);
    }

    public void connectToQM()
    {
        Log.i(TAG, "Binding to QM...");
        FlowfenceConnection.bind(this, new FlowfenceConnection.Callback() {
            @Override
            public void onConnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Bound to QM");
                onQMConnect(conn);
            }
        });
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {
                sendBMPStatic = oconn.resolveStatic(void.class, CamQM.class, "sendBMP", Bitmap.class, int.class, int.class, long.class);
            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    private void onQMConnect(FlowfenceConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to QM", Toast.LENGTH_SHORT);
        t.show();

        resolve();
    }

    public void newBmp(Bitmap bmp, int index, int opcode)
    {

        if(sendBMPStatic != null)
        {
            try {
                long now = SystemClock.uptimeMillis();
                sendBMPStatic.arg(bmp).arg(index).arg(opcode).arg(now).call();

            } catch(Exception e)
            {
                Log.e(TAG, "Error: " + e);
            }
        }
    }

    public void _newBmp()
    {
        //read in a bitmap from wherever
        //pass that bitmap to CamQM

        //Operation Order is VERY IMPORTANT
        //CamQM expects things to arrive in this order

        //first transfer the ref images
        //ref image is opcode 2
        //nonref image is opcode 3
        Bitmap ref = ds.getRef();
        Bitmap nonref = ds.getNonRef();

        if(sendBMPStatic != null)
        {
            try {
                //send the "ref" image
                sendBMPStatic.arg(ref).arg(-1).arg(2).arg(0l).call();

                //send the "nonref" image
                sendBMPStatic.arg(nonref).arg(-1).arg(3).arg(0l).call();
            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }

        //now transfer the test images
        for(int i = 0; i < numImages; i++)
        {
            Bitmap _bmp = ds.getBitmap(i);

            if(sendBMPStatic != null)
            {
                try {
                    sendBMPStatic.arg(_bmp).arg(i).arg(1).arg(0l).call();
                } catch(Exception e)
                {
                    Log.e(TAG, "error: " + e);
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}

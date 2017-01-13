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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import edu.umich.oasis.client.OASISConnection;
import edu.umich.oasis.client.Soda;

public class FrameInjectorMain extends Activity {

    private static final String TAG = "FrameInjectorMain";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static View image = null;
    private static TextView text = null;

    private static PowerManager.WakeLock wakeLock = null;

    private static int averageIndex = 0;
    private static final int averageArraySize = 4;
    private static final int[] averageArray = new int[averageArraySize];

    public static enum TYPE {
        GREEN, RED
    };

    private static TYPE currentType = TYPE.GREEN;

    public static TYPE getCurrent() {
        return currentType;
    }

    private static int beatsIndex = 0;
    private static final int beatsArraySize = 3;
    private static final int[] beatsArray = new int[beatsArraySize];
    private static double beats = 0;
    private static long startTime = 0;

    // Throughput calculations
    // every 10 seconds, get the number of items in the bucket
    // the bucket is incremented once every "processing loop"
    static AtomicInteger bucket = new AtomicInteger(0);
    Timer thrTimer, finishTimer;
    Throughput thr;
    FinishTask finishTask;
    long interval = 1; //in seconds
    long experimentLength = 120; //in seconds

    private static final boolean DEBUG_TPUT = true;

    //OASIS stuff
    OASISConnection oconn = null;
    static Soda.S4<byte [], Integer, Integer, Long, Void> newFrameStatic = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frame_injector_main);

        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        image = findViewById(R.id.image);
        text = (TextView) findViewById(R.id.text);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

        /*thrTimer = new Timer();
        thr = new Throughput();*/

        finishTimer = new Timer();
        finishTask = new FinishTask();
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

    private void onOASISConnect(OASISConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to OASIS", Toast.LENGTH_SHORT);
        t.show();

        resolve();
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {
                newFrameStatic = oconn.resolveStatic(void.class, FrameSoda.class, "newFrame", byte [].class, int.class, int.class, long.class);
            } catch(Exception e)
            {
                Log.e(TAG, "error: " + e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

        wakeLock.acquire();

        camera = Camera.open();

        connectToOASIS();

        startTime = System.currentTimeMillis();

        //thrTimer.schedule(thr, 0, interval * 1000); //once every X seconds


        //signal HRService to start its poller thread
        if(DEBUG_TPUT) {
            Intent startPoll = new Intent("startPoll");
            sendBroadcast(startPoll);
        }

        finishTimer.schedule(finishTask, experimentLength * 1000);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        wakeLock.release();

        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;

        //thrTimer.cancel();
    }

    private static Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

        long startL, endL;

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) throw new NullPointerException();
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) throw new NullPointerException();

            long deliveryTime = SystemClock.elapsedRealtimeNanos();

            //send frame to FrameSoda
            if(newFrameStatic != null)
            {
                try {
                    long startTime = SystemClock.uptimeMillis();
                    newFrameStatic.arg(data).arg(size.width).arg(size.height).arg(startTime).call();
                } catch(Exception e)
                {
                    Log.e(TAG, "error: " + e);
                }
            }

            //update bucket
            //bucket.addAndGet(1);

            //latency
            //endL = System.nanoTime();
            //Log.i("LATENCY", "" + ((double) (endL - startL) / 1000000.0d)); //in ms
            //Log.i("LATENCY", "" + (endL - startL));
        }
    };

    public static String SHAsum(byte[] convertme) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return byteArray2Hex(md.digest(convertme));
    }

    private static String byteArray2Hex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private static SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e(TAG, "Exception in setPreviewDisplay()");
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea < resultArea) result = size;
                }
            }
        }

        return result;
    }

    class Throughput extends TimerTask
    {
        public void run()
        {
            double thrVal = (double) bucket.getAndSet(0) / (double) interval;
            Log.i("THROUGHPUT", "" + thrVal);
        }
    }

    class FinishTask extends TimerTask
    {
        public void run()
        {

            //fire a broadcast so that HRService knows to stop its throughput polling timer task
            Intent stopPoll = new Intent("stopPoll");
            sendBroadcast(stopPoll);

            finish();
        }
    }
}

package com.jwetherell.heart_rate_monitor;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This class extends Activity to handle a picture preview, process the preview
 * for a red values and determine a heart beat.
 * 
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public class HeartRateMonitor extends Activity {

    private static final String TAG = "HeartRateMonitor";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static View image = null;
    private static TextView text = null;

    private static WakeLock wakeLock = null;

    private static int averageIndex = 0;
    private static final int averageArraySize = 4;
    private static final int[] averageArray = new int[averageArraySize];

    static double latencyAvg = 0.0;
    static long latencyCount = 0;
    static double latencySum = 0.0;

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
    private static final boolean DEBUG_LATENCY = false;

    static long firstTS;
    static boolean firstTSSet = false;
    //static int counter = 0;
    static long startL, endL;

    static AtomicInteger counter = new AtomicInteger(0);

    //private static ArrayList<Double> decode = new ArrayList<Double>();
    //private static ArrayList<Double> point1 = new ArrayList<Double>();
    //private static ArrayList<Double> point2 = new ArrayList<Double>();

    //static {
    //    decode.clear();
    //    point1.clear();
    //    point2.clear();
    //}


        
    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        image = findViewById(R.id.image);
        text = (TextView) findViewById(R.id.text);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

        thrTimer = new Timer();
        thr = new Throughput();
        
        finishTimer = new Timer();
        finishTask = new FinishTask();
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

        startTime = System.currentTimeMillis();

        if(DEBUG_TPUT) {
            thrTimer.schedule(thr, 0, interval * 1000); //once every X seconds
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
        
        thrTimer.cancel();
    }

    private static PreviewCallback previewCallback = new PreviewCallback() {
    	


        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) throw new NullPointerException();
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) throw new NullPointerException();

            int width = size.width;
            int height = size.height;

            newFrame(data, width, height);
        }
    };

    static void newFrame(byte [] data, int width, int height)
    {
        if(!firstTSSet)
        {
            firstTS = SystemClock.uptimeMillis();
            firstTSSet = true;
        }

        Log.i(TAG, "frame size byte: " + data.length + ", w: " + width + ", h: " + height);


        if (!processing.compareAndSet(false, true))
        {
            //Log.i("LATENCY", "premat");
            return;
        }



        startL = System.nanoTime();

        int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width);

        //breakLat(decode, "decode");

        // Log.i(TAG, "imgAvg="+imgAvg);
        if (imgAvg == 0 || imgAvg == 255) {

            if(DEBUG_TPUT) {
                //update bucket
                //bucket.addAndGet(1);
                counter.addAndGet(1);
                //counter += 1;
            }

            //latency
            if(DEBUG_LATENCY) {
                endL = System.nanoTime();
                double latencyInst = ((double) (endL - startL) / 1000000.0d);
                Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

                latencyCount += 1;
                latencySum += latencyInst;
                latencyAvg = latencySum / (double) latencyCount;
                Log.i("LAT_AVG", String.valueOf(latencyAvg));
                //Log.i("LATENCY_1", "" + (endL - startL));
            }

            processing.set(false);
            return;
        }

        int averageArrayAvg = 0;
        int averageArrayCnt = 0;
        for (int i = 0; i < averageArray.length; i++) {
            if (averageArray[i] > 0) {
                averageArrayAvg += averageArray[i];
                averageArrayCnt++;
            }
        }

        int rollingAverage = (averageArrayCnt > 0) ? (averageArrayAvg / averageArrayCnt) : 0;
        TYPE newType = currentType;
        if (imgAvg < rollingAverage) {
            newType = TYPE.RED;
            if (newType != currentType) {
                beats++;
                Log.d(TAG, "BEAT!! beats="+beats);
            }
        } else if (imgAvg > rollingAverage) {
            newType = TYPE.GREEN;
        }

        if (averageIndex == averageArraySize) averageIndex = 0;
        averageArray[averageIndex] = imgAvg;
        averageIndex++;

        // Transitioned from one state to another to the same
        if (newType != currentType) {
            currentType = newType;
            //image.postInvalidate();
        }

        //breakLat(point1, "point1");

        long endTime = System.currentTimeMillis();
        double totalTimeInSecs = (endTime - startTime) / 1000d;
        if (totalTimeInSecs >= 10) {
            double bps = (beats / totalTimeInSecs);
            int dpm = (int) (bps * 60d);
            if (dpm < 30 || dpm > 180) {
                startTime = System.currentTimeMillis();
                beats = 0;
                processing.set(false);

                //update bucket
                if(DEBUG_TPUT) {
                    //bucket.addAndGet(1);
                    counter.addAndGet(1);
                    //counter += 1;
                }

                //latency
                if(DEBUG_LATENCY) {
                    endL = System.nanoTime();
                    double latencyInst = ((double) (endL - startL) / 1000000.0d);
                    Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

                    latencyCount += 1;
                    latencySum += latencyInst;
                    latencyAvg = latencySum / (double) latencyCount;
                    Log.i("LAT_AVG", String.valueOf(latencyAvg));
                    //Log.i("LATENCY_1", "" + (endL - startL));
                }

                return;
            }

            // Log.d(TAG,
            // "totalTimeInSecs="+totalTimeInSecs+" beats="+beats);

            if (beatsIndex == beatsArraySize) beatsIndex = 0;
            beatsArray[beatsIndex] = dpm;
            beatsIndex++;

            int beatsArrayAvg = 0;
            int beatsArrayCnt = 0;
            for (int i = 0; i < beatsArray.length; i++) {
                if (beatsArray[i] > 0) {
                    beatsArrayAvg += beatsArray[i];
                    beatsArrayCnt++;
                }
            }
            int beatsAvg = (beatsArrayAvg / beatsArrayCnt);
            //text.setText(String.valueOf(beatsAvg));

            Log.i("EARLENCE", String.valueOf(beatsAvg));

            startTime = System.currentTimeMillis();
            beats = 0;
        }
        processing.set(false);

        //update bucket
        if(DEBUG_TPUT) {
            //bucket.addAndGet(1);
            counter.addAndGet(1);
            //counter += 1;
        }

        //breakLat(point2, "point2");

        //latency
        if(DEBUG_LATENCY) {
            endL = System.nanoTime();
            double latencyInst = ((double) (endL - startL) / 1000000.0d);
            Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

            latencyCount += 1;
            latencySum += latencyInst;
            latencyAvg = latencySum / (double) latencyCount;
            Log.i("LAT_AVG", String.valueOf(latencyAvg));
            //Log.i("LATENCY_1", "" + (endL - startL));
        }
    }

    /*static void breakLat(ArrayList<Double> acc, String tag)
    {
        endL = System.nanoTime();
        double lat = ((double) (endL - startL) / 1000000.0d); //in ms

        acc.add(lat);

        avgLat(acc, tag);

        startL = System.nanoTime();
    }

    static void avgLat(ArrayList<Double> acc, String tag)
    {
        double sum = 0.0;
        for(Double d : acc)
            sum += d;

        Log.i(TAG, tag + ": " + sum / acc.size());
    }*/

    private static SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {


        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e(TAG, "Exception in setPreviewDisplay()");
            }
        }

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
            int cVal = counter.getAndSet(0);
            //int cVal = counter;
    		//double thrVal = (double) bucket.getAndSet(0) / (double) interval;
            long now = SystemClock.uptimeMillis();
            double duration = ((double) (now - firstTS)) / 1000.0; //seconds
            double fps = ((double) (cVal)) / duration;

    		Log.i("THROUGHPUT", "fps: " + fps);
            //Log.i("THROUGHPUT", "duration: " + duration);

            //reset stats
            //counter = 0;
            firstTS = SystemClock.uptimeMillis();
            firstTSSet = false;
    	}
    }
    
    class FinishTask extends TimerTask {
        public void run() {
            finish();
        }
    }
}

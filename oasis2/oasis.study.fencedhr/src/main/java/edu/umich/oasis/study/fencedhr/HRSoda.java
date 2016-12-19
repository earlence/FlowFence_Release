package edu.umich.oasis.study.fencedhr;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umich.oasis.common.OASISContext;

/**
 * Created by earlence on 2/9/16.
 */
public class HRSoda implements Parcelable
{
    private static final String TAG = "HRSoda";
    public static final String TPUTKV = "tput_store";

    //latency computations
    static long startL, endL;

    //heart-rate calculations
    private static final AtomicBoolean processing = new AtomicBoolean(false);
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
    static long startTime = 0;

    private static final boolean DEBUG_TPUT = true;
    private static final boolean DEBUG_LATENCY = false;

    private static ArrayList<Double> decode = new ArrayList<Double>();
    private static ArrayList<Double> point1 = new ArrayList<Double>();
    private static ArrayList<Double> point2 = new ArrayList<Double>();

    static double latencyAvg = 0.0;
    static long latencyCount = 0;
    static double latencySum = 0.0;

    public HRSoda()
    {
    }

    static {
        decode.clear();
        point1.clear();
        point2.clear();
    }

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

    public static void newFrame(byte [] data, int width, int height, long eventStartTime)
    {
        /*if (!processing.compareAndSet(false, true))
        {
            return;
        }

        //startL = System.nanoTime();

        int imgAvg = decodeYUV420SPtoRedAvg(data.clone(), height, width);

        //breakLat(decode, "decode");

        // Log.i(TAG, "imgAvg="+imgAvg);
        if (imgAvg == 0 || imgAvg == 255) {


            //throughput
            if(DEBUG_TPUT)
                tput();

            //latency
            if(DEBUG_LATENCY) {
                endL = SystemClock.uptimeMillis();
                //double latencyInst = ((double) (endL - startL) / 1000000.0d);
                double latencyInst = ((double) (endL - eventStartTime));
                Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

                //latencyCount += 1;
                //latencySum += latencyInst;
                //latencyAvg = latencySum / (double) latencyCount;
                //Log.i("LAT_AVG", String.valueOf(latencyAvg));
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

                //throughput
                if(DEBUG_TPUT)
                    tput();

                if(DEBUG_LATENCY) {
                    endL = SystemClock.uptimeMillis();
                    //double latencyInst = ((double) (endL - startL) / 1000000.0d);
                    double latencyInst = ((double) (endL - eventStartTime));
                    Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

                    //latencyCount += 1;
                    //latencySum += latencyInst;
                    //latencyAvg = latencySum / (double) latencyCount;
                    //Log.i("LAT_AVG", String.valueOf(latencyAvg));
                    //Log.i("LATENCY_1", "" + (endL - startL));
                }

                return;
            }

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

            Log.i(TAG, String.valueOf(beatsAvg));

            startTime = System.currentTimeMillis();
            beats = 0;
        }
        processing.set(false);

        //breakLat(point2, "point2");*/

        //throughput
        if(DEBUG_TPUT)
            tput();

        //latency
        if(DEBUG_LATENCY) {
            endL = SystemClock.uptimeMillis();
            //double latencyInst = ((double) (endL - startL) / 1000000.0d);
            double latencyInst = ((double) (endL - eventStartTime));
            Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

            //latencyCount += 1;
            //latencySum += latencyInst;
            //latencyAvg = latencySum / (double) latencyCount;
            //Log.i("LAT_AVG", String.valueOf(latencyAvg));
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

    static void tput()
    {
        SharedPreferences myprefs = OASISContext.getInstance().getSharedPreferences(TPUTKV, 0);
        int counter = myprefs.getInt("counter", 0);

        SharedPreferences.Editor edit = myprefs.edit();
        edit.putInt("counter", counter+1);
        if (counter == 0) {
            edit.putLong("ts", SystemClock.uptimeMillis());
        }
        edit.apply();
    }

    private static int decodeYUV420SPtoRedSum(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = 0;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv420sp[yp]) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                int pixel = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
                int red = (pixel >> 16) & 0xff;
                sum += red;
            }
        }
        return sum;
    }

    /**
     * Given a byte array representing a yuv420sp image, determine the average
     * amount of red in the image. Note: returns 0 if the byte array is NULL.
     *
     * @param yuv420sp
     *            Byte array representing a yuv420sp image
     * @param width
     *            Width of the image.
     * @param height
     *            Height of the image.
     * @return int representing the average amount of red in the image.
     */
    public static int decodeYUV420SPtoRedAvg(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = decodeYUV420SPtoRedSum(yuv420sp, width, height);
        return (sum / frameSize);
    }

    //boiler-plate parcel serialize/deserialize
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {

    }

    public static final Parcelable.Creator<HRSoda> CREATOR = new Parcelable.Creator<HRSoda>()
    {
        public HRSoda createFromParcel(Parcel in) {
            return new HRSoda(in);
        }

        public HRSoda[] newArray(int size) {
            return new HRSoda[size];
        }
    };

    private HRSoda(Parcel in)
    {

    }
}

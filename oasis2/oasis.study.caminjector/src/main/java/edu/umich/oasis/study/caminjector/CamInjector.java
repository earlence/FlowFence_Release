package edu.umich.oasis.study.caminjector;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import edu.umich.oasis.client.OASISConnection;
import edu.umich.oasis.client.Soda;

// This service pushes a bitmap into CamSoda.
// CamSoda then fires an event with that bitmap
public class CamInjector extends Service {
    private static final String TAG = "CamInjector";

    private static final String TIMING_TAG_START = "timeStart";
    private static final String TIMING_TAG_END = "timeEnd";
    private static final boolean DEBUG_TIME = true;

    OASISConnection oconn = null;

    Soda.S4<Bitmap, Integer, Integer, Long, Void> sendBMPStatic = null;

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
        connectToOASIS();
        ds = new DataSource(numImages);
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
                sendBMPStatic = oconn.resolveStatic(void.class, CamSoda.class, "sendBMP", Bitmap.class, int.class, int.class, long.class);
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
        //pass that bitmap to CamSoda

        //Operation Order is VERY IMPORTANT
        //CamSoda expects things to arrive in this order

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

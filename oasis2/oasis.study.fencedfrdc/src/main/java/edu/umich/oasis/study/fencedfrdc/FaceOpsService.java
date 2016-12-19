package edu.umich.oasis.study.fencedfrdc;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import edu.umich.oasis.client.OASISConnection;
import edu.umich.oasis.client.Soda;
import edu.umich.oasis.common.SodaDescriptor;

public class FaceOpsService extends Service {
    private static final String TAG = "FaceOpsService";
    OASISConnection oconn = null;

    Soda.S4<Integer, Integer, Bitmap, Long, Void> bmpRxStatic = null;

    public FaceOpsService()
    {
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
    }

    public void resolve()
    {
        if(oconn != null)
        {
            try {
                bmpRxStatic = oconn.resolveStatic(void.class, FRDCSoda.class, "bmpRx", int.class, int.class, Bitmap.class, long.class);

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
        SodaDescriptor sd = bmpRxStatic.getDescriptor();
        ComponentName cn = new ComponentName("edu.umich.oasis.study.caminjector", "cameraBMPChannel");

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

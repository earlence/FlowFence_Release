package edu.umich.oasis.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import edu.umich.oasis.smartthings.SmartThingsService;

public final class OASISService extends Service
{
    private static final String TAG = "OASIS.Service";
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        OASISApplication.getInstance().onServiceCreate(this);

        //run the ctor once so that the SmartThingsService
        //object is created, and later accesses will return a single instance
        SmartThingsService.getInstance();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        OASISApplication.getInstance().onServiceDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return OASISApplication.getInstance().getBinder();
    }

    private int startCount;
    private boolean wasStarted;

    public synchronized void addRef() {
        /*if (startCount++ == 0) {
            Log.i(TAG, "Unbinding with SODAs still running; starting service");
            startService(new Intent(this, OASISService.class));
            wasStarted = true;
        }*/
    }

    public synchronized void release() {
        /*if (--startCount == 0 && wasStarted) {
            Log.i(TAG, "All references dropped; stopping service");
            stopSelf();
            wasStarted = false;
        }*/
    }
}

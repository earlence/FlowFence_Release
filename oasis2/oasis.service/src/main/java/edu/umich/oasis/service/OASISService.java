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

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

package edu.umich.flowfence.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import edu.umich.flowfence.smartthings.SmartThingsService;

public final class FlowfenceService extends Service
{
    private static final String TAG = "FF.Service";
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        FlowfenceApplication.getInstance().onServiceCreate(this);

        //run the ctor once so that the SmartThingsService
        //object is created, and later accesses will return a single instance
        SmartThingsService.getInstance();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        FlowfenceApplication.getInstance().onServiceDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return FlowfenceApplication.getInstance().getBinder();
    }

    private int startCount;
    private boolean wasStarted;

    public synchronized void addRef() {
    }

    public synchronized void release() {
    }
}

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PackageInstalledReceiver extends BroadcastReceiver {
    private static final String TAG = "FF.PackageReceiver";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Got intent: "+intent);

        if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED) ||
            intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED) ||
            intent.getAction().equals(Intent.ACTION_PACKAGE_RESTARTED)) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (context.getPackageName().equals(packageName)) {
                return;
            }
            Log.i(TAG, "Package: "+packageName);
            FlowfenceApplication.getInstance().onPackageRemoved(packageName);
        }
    }
}

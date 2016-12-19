package edu.umich.oasis.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by jpaupore on 2/7/16.
 */
public class PackageInstalledReceiver extends BroadcastReceiver {
    private static final String TAG = "OASIS.PackageReceiver";
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
            OASISApplication.getInstance().onPackageRemoved(packageName);
        }
    }
}

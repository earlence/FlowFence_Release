package edu.umich.oasis.client;

import android.app.Application;
import android.content.Context;

/**
 * Created by jpaupore on 12/10/15.
 */
public class OASISApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        OASISFramework.install(this);
    }
}

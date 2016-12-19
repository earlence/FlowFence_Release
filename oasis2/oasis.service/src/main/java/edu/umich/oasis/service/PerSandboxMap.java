package edu.umich.oasis.service;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by jpaupore on 1/26/15.
 */
public class PerSandboxMap<T> extends Hashtable<Sandbox, T> {
    private static final String TAG = "OASIS.PerSandboxMap";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private static final long serialVersionUID = 1L;
    public PerSandboxMap() {
        super();
    }

    public PerSandboxMap(int capacity) {
        super(capacity);
    }

    public PerSandboxMap(int capacity, float loadFactor) {
        super(capacity, loadFactor);
    }

    public PerSandboxMap(Map<? extends Sandbox, ? extends T> map) {
        super(map);
    }

    private void register() {
        mHandler = Sandbox.g_onDisconnected.register(this, new Sandbox.EventHandler() {
            @Override
            public boolean onEvent(String event, Sandbox sender, Object args) throws Exception {
                if (localLOGD) {
                    Log.d(TAG, "Removing disconnected sandbox " + sender.toString());
                }
                PerSandboxMap.this.remove(sender);
                if (localLOGV) {
                    Log.v(TAG, "New mappings: "+this);
                }
                return false;
            }
        });
    }

    protected Sandbox.EventHandler mHandler;

    // Instance initializer block.
    {
        register();
    }
}

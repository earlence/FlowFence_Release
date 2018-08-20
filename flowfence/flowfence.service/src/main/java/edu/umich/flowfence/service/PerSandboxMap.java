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

import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

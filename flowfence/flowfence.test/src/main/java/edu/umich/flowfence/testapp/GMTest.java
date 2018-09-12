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

package edu.umich.flowfence.testapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.widget.Toast;

import edu.umich.flowfence.common.IDynamicAPI;
import edu.umich.flowfence.common.FlowfenceContext;

public class GMTest {
    private static class State {
        public float speed;
        public float heading;
    }

    private static final String PACKAGE = "com.gm.flowfence.ecu.receiver";
    private static final String STORE = "com.gm.flowfence.ecu.nav";
    private static final String SPEED_KEY = "speed";
    private static final String HEADING_KEY = "heading";

    private static State getState() {
        try {
            SharedPreferences prefs = FlowfenceContext.getInstance()
                    .createPackageContext(PACKAGE, 0)
                    .getSharedPreferences(STORE, Context.MODE_WORLD_READABLE);

            State s = new State();
            s.speed = prefs.getFloat(SPEED_KEY, 0.0f);
            s.heading = prefs.getFloat(HEADING_KEY, 0.0f);
            return s;
        } catch (PackageManager.NameNotFoundException e) {
            // can't happen
            return new State();
        }
    }

    public static void toastValue() {
        State s = getState();
        IDynamicAPI toast = (IDynamicAPI) FlowfenceContext.getInstance().getTrustedAPI("toast");
        toast.invoke("showText", String.format("%.1f mph @ %.1f deg", s.speed, s.heading), Toast.LENGTH_LONG);
    }

    private static final String[] DIRECTIONS = {
            "N","NNE","NE","ENE","E","ESE", "SE", "SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"
    };

    public static void pushValue() {
        State s = getState();
        IDynamicAPI push = (IDynamicAPI) FlowfenceContext.getInstance().getTrustedAPI("push");

        int index = (int)((s.heading/22.5f)+0.5f);
        String direction = DIRECTIONS[index%16];

        StringBuilder body = new StringBuilder()
                .append("You're traveling ")
                .append(direction)
                .append(".\n\n")
                .append("You're going ")
                .append(s.speed)
                .append(" MPH.");

        if (s.speed >= 75.0f) {
            body.append("\n\nBetter hope the cops aren't around.");
        }

        push.invoke("sendPush", "Dashboard Status", body.toString());
    }

    public static void pushNonValue() {
        IDynamicAPI push = (IDynamicAPI) FlowfenceContext.getInstance().getTrustedAPI("push");
        push.invoke("sendPush", "Hello, world!", "This data is not tainted.");
    }
}

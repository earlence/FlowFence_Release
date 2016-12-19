package edu.umich.oasis.testapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.widget.Toast;

import edu.umich.oasis.common.IDynamicAPI;
import edu.umich.oasis.common.OASISContext;

/**
 * Created by jpaupore on 10/6/15.
 */
public class GMTest {
    private static class State {
        public float speed;
        public float heading;
    }

    private static final String PACKAGE = "com.gm.oasis.ecu.receiver";
    private static final String STORE = "com.gm.oasis.ecu.nav";
    private static final String SPEED_KEY = "speed";
    private static final String HEADING_KEY = "heading";

    private static State getState() {
        try {
            SharedPreferences prefs = OASISContext.getInstance()
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
        IDynamicAPI toast = (IDynamicAPI)OASISContext.getInstance().getTrustedAPI("toast");

        toast.invoke("showText", String.format("%.1f mph @ %.1f deg", s.speed, s.heading), Toast.LENGTH_LONG);
    }

    private static final String[] DIRECTIONS = {
            "N","NNE","NE","ENE","E","ESE", "SE", "SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"
    };

    public static void pushValue() {
        State s = getState();
        IDynamicAPI push = (IDynamicAPI)OASISContext.getInstance().getTrustedAPI("push");

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
        IDynamicAPI push = (IDynamicAPI)OASISContext.getInstance().getTrustedAPI("push");

        push.invoke("sendPush", "Hello, world!", "This data is not tainted.");
    }
}

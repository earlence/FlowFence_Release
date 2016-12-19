package edu.umich.oasis.study.smartdevresponderexemplar;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import java.util.List;

public class SmartDevRespExemplarActivity extends Activity {

    private static String FIREBASE_URL = "https://blinding-inferno-7958.firebaseio.com/";
    private static String LOC_KEY = "location";
    Firebase firebaseRef;

    private static final String TAG = "SDRExemplar";

    String history = "";

    private static final String TIMING_TAG_START = "timeStart";
    private static final String TIMING_TAG_END = "timeEnd";
    private static final boolean DEBUG_TIME = true;
    private static final boolean CCT_SWITCHOPS = true; //if true, cuts out switch-related ops

    Button cmdSmartThings, cmdLogin;

    long start = 0, end = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_dev_resp_exemplar);

        cmdSmartThings = (Button) findViewById(R.id.cmdSmartThings);
        cmdLogin = (Button) findViewById(R.id.cmdLogin);

        cmdSmartThings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initST();
            }
        });

        cmdLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initFirebase();
            }
        });
    }

    private void initST()
    {
        SmartThingsService.getInstance();
    }

    private void initFirebase()
    {
        Firebase.setAndroidContext(this);
        firebaseRef = new Firebase(FIREBASE_URL);

        firebaseRef.authWithPassword("test_rx@gmail.com", "test123", new Firebase.AuthResultHandler()
        {
            @Override
            public void onAuthenticated(AuthData authData)
            {
                Toast t = Toast.makeText(getApplicationContext(), "Authenticated to Firebase", Toast.LENGTH_SHORT);
                t.show();

                rx();
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError)
            {
                Log.i(TAG, "Firebase Auth Error: " + firebaseError);
            }
        });
    }

    private void rx()
    {
        firebaseRef.child(LOC_KEY).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String loc = (String) dataSnapshot.getValue();
                Log.i(TAG, "updating loc to: " + loc);

                if(DEBUG_TIME)
                {
                    //start = System.nanoTime();
                    //Log.i(TIMING_TAG_START, "" + start);
                }

                toggleSwitch(loc);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    private void toggleSwitch(String presence)
    {
        if(!history.equals(presence)) {



            if (presence.equals("home")) {
                Log.i(TAG, "let there be light!");


                List<SmartSwitch> switches = SmartThingsService.getInstance().getSwitches();

                if(switches != null) {
                    for (SmartSwitch ssw : switches) {
                        SmartThingsService.getInstance().switchOnOff("on", ssw.getSwitchId());
                    }
                }
                else
                    Log.e(TAG, "no switches available");

                //talk to SmartThings and switch on lights
            } else if (presence.equals("away")) {
                Log.i(TAG, "lights off!");


                List<SmartSwitch> switches = SmartThingsService.getInstance().getSwitches();

                if(switches != null) {
                    for (SmartSwitch ssw : switches) {
                        SmartThingsService.getInstance().switchOnOff("off", ssw.getSwitchId());
                    }
                }
                else
                    Log.e(TAG, "no switches available");
            }

            history = presence;

            if(DEBUG_TIME)
            {
                //end = System.nanoTime();
                //Log.i(TIMING_TAG_END, "" + end);
                Log.i("TIMING_TAG", "" + SystemClock.uptimeMillis());


            }
        }
    }
}

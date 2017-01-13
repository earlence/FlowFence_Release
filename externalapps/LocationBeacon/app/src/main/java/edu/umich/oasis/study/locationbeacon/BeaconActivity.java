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
 
package edu.umich.oasis.study.locationbeacon;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;

public class BeaconActivity extends Activity {

    private static final String TAG = "BeaconActivity";

    Button cmdLogin, cmdHome, cmdAway;
    Firebase firebaseRef = null;

    private static String FIREBASE_URL = "https://blinding-inferno-7958.firebaseio.com/";
    private static String LOC_KEY = "location";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beacon);

        cmdLogin = (Button) findViewById(R.id.cmdLogin);
        cmdHome = (Button) findViewById(R.id.cmdHome);
        cmdAway = (Button) findViewById(R.id.cmdAway);

        cmdLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initFirebase();
            }
        });

        cmdHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(firebaseRef != null)
                {
                    Log.i("BEACON", "" + SystemClock.uptimeMillis());
                    firebaseRef.child(LOC_KEY).setValue("home");
                }

            }
        });

        cmdAway.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(firebaseRef != null)
                {
                    Log.i("BEACON", "" + SystemClock.uptimeMillis());
                    firebaseRef.child(LOC_KEY).setValue("away");
                }

            }
        });
    }

    private void initFirebase()
    {
        Firebase.setAndroidContext(this);
        firebaseRef = new Firebase(FIREBASE_URL);

        firebaseRef.authWithPassword("test_tx@gmail.com", "test123", new Firebase.AuthResultHandler()
        {
            @Override
            public void onAuthenticated(AuthData authData)
            {
                Toast t = Toast.makeText(getApplicationContext(), "Firebase authenticated", Toast.LENGTH_SHORT);
                t.show();
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError)
            {
                Log.i(TAG, "Firebase Auth Error: " + firebaseError);
            }
        });
    }
}

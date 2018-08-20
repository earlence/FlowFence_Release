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

package edu.umich.flowfence.study.oasisstudyskeleton;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import edu.umich.flowfence.client.OASISConnection;
import edu.umich.flowfence.client.Sealed;
import edu.umich.flowfence.client.Soda;


public class MainActivity extends Activity {

    //our connection to the OASIS system
    OASISConnection oconn = null;
    Soda.S0<TestSoda> ctor = null;

    Button cmdConnect, cmdSodaOps, cmdKVTest;

    public static String STORE_NAME = "EKVStore";
    public static String LOC_KEY = "presence";

    private static final String TAG = "OASIS.skeleton/Main";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cmdConnect = (Button) findViewById(R.id.cmdConnect);
        cmdSodaOps = (Button) findViewById(R.id.cmdSodaOps);
        cmdKVTest = (Button) findViewById(R.id.cmdKVTest);

        cmdConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToOASIS();
            }
        });

        cmdSodaOps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simpleMethodCallAndReturn();
            }
        });

        cmdKVTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                putValueUsingSoda("home");
                readValueInSoda();
            }
        });
    }

    public void connectToOASIS()
    {
        Log.i(TAG, "Binding to OASIS...");
        OASISConnection.bind(this, new OASISConnection.Callback() {
            @Override
            public void onConnect(OASISConnection conn) throws Exception {
                Log.i(TAG, "Bound to OASIS");
                onOASISConnect(conn);
            }
        });
    }

    private void onOASISConnect(OASISConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to OASIS", Toast.LENGTH_SHORT);
        t.show();
    }

    //you can only access a KV store from inside a soda
    private void putValueUsingSoda(String value)
    {


        if(oconn != null)
        {
            try {

                //get the TestSoda constructor
                ctor = oconn.resolveConstructor(TestSoda.class);

                //create the object in the sandbox, and get a ref to it
                Sealed<TestSoda> soda1 = ctor.call();

                //read the LOC_KEY value from inside SODA
                //void putLoc(String)

                //implicit "this", param1Type, RetType = resolve(ReturnType, Clazz, methodAsString, param1Type
                Soda.S2<TestSoda, String, Void> putLoc = oconn.resolveInstance(Void.class, TestSoda.class, "putLoc", String.class);
                putLoc.arg(soda1).arg("home").call();

            } catch(Exception e)
            {
                Log.i(TAG, "error: " + e);
            }
        }
    }

    private void readValueInSoda()
    {
        if(oconn != null)
        {
            try {

                //get the TestSoda constructor
                ctor = oconn.resolveConstructor(TestSoda.class);

                //create the object in the sandbox, and get a ref to it
                Sealed<TestSoda> soda1 = ctor.call();

                //read the LOC_KEY value from inside SODA
                Soda.S1<TestSoda, Void> readLoc = oconn.resolveInstance(Void.class, TestSoda.class, "readLoc");
                readLoc.arg(soda1).call();

            } catch(Exception e)
            {
                Log.i(TAG, "error: " + e);
            }
        }
    }

    private void simpleMethodCallAndReturn()
    {
        if(oconn != null)
        {
            try {

                //get the TestSoda constructor
                ctor = oconn.resolveConstructor(TestSoda.class);

                //create the object in the sandbox, and get a ref to it
                Sealed<TestSoda> soda1 = ctor.call();

                //get a ref the incrementValue method in TestSoda object that was created in the sandbox
                Soda.S1<TestSoda, Void> incrVal = oconn.resolveInstance(void.class, TestSoda.class, "incrementValue");

                //call the incrementValue method in TestSoda that is loaded in the sandbox
                //all Java methods implicitly take the "this" as the first param. In OASIS, we
                //pass "this" explicitly.
                incrVal.arg(soda1).call();

                //get the incremented value
                Soda.S1<TestSoda, Integer> getVal = oconn.resolveInstance(int.class, TestSoda.class, "getValue");
                Sealed<Integer> retVal = getVal.arg(soda1).call();

                //pass in the value from TestSoda into another instance of TestSoda
                Sealed<TestSoda> soda2 = ctor.call();
                //order is [parameters], return value while declaring ref to SODA
                //order is return type, clazz, methodNameAsString, [parameter list] while doing resolution
                Soda.S2<TestSoda, Integer, Void> setValue = oconn.resolveInstance(void.class, TestSoda.class, "setValue", int.class);

                setValue.arg(soda2).arg(retVal).call();

            } catch(Exception e)
            {
                Log.i(TAG, "error: " + e);
            }
        }
    }
}

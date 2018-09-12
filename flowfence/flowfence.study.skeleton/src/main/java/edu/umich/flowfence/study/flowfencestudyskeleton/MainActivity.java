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

package edu.umich.flowfence.study.flowfencestudyskeleton;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.Sealed;
import edu.umich.flowfence.client.QuarentineModule;


public class MainActivity extends Activity {

    //our connection to the Flowfence system
    FlowfenceConnection oconn = null;
    QuarentineModule.S0<TestQM> ctor = null;

    Button cmdConnect, cmdQMOps, cmdKVTest;

    public static String STORE_NAME = "EKVStore";
    public static String LOC_KEY = "presence";

    private static final String TAG = "FlowFence.skeleton/Main";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cmdConnect = (Button) findViewById(R.id.cmdConnect);
        cmdQMOps = (Button) findViewById(R.id.cmdQMOps);
        cmdKVTest = (Button) findViewById(R.id.cmdKVTest);

        cmdConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToFlowfence();
            }
        });

        cmdQMOps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simpleMethodCallAndReturn();
            }
        });

        cmdKVTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                putValueUsingQM("home");
                readValueInQM();
            }
        });
    }

    public void connectToFlowfence()
    {
        Log.i(TAG, "Binding to FlowFence...");
        FlowfenceConnection.bind(this, new FlowfenceConnection.Callback() {
            @Override
            public void onConnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Bound to FlowFence");
                onFlowFenceConnect(conn);
            }
        });
    }

    private void onFlowFenceConnect(FlowfenceConnection conn)
    {
        oconn = conn;
        Toast t = Toast.makeText(getApplicationContext(), "connected to FlowFence", Toast.LENGTH_SHORT);
        t.show();
    }

    //you can only access a KV store from inside a QM
    private void putValueUsingQM(String value)
    {


        if(oconn != null)
        {
            try {

                //get the TestQM constructor
                ctor = oconn.resolveConstructor(TestQM.class);

                //create the object in the sandbox, and get a ref to it
                Sealed<TestQM> qm1 = ctor.call();

                //read the LOC_KEY value from inside QM
                //void putLoc(String)

                //implicit "this", param1Type, RetType = resolve(ReturnType, Clazz, methodAsString, param1Type
                QuarentineModule.S2<TestQM, String, Void> putLoc = oconn.resolveInstance(Void.class, TestQM.class, "putLoc", String.class);
                putLoc.arg(qm1).arg("home").call();

            } catch(Exception e)
            {
                Log.i(TAG, "error: " + e);
            }
        }
    }

    private void readValueInQM()
    {
        if(oconn != null)
        {
            try {

                //get the TestQM constructor
                ctor = oconn.resolveConstructor(TestQM.class);

                //create the object in the sandbox, and get a ref to it
                Sealed<TestQM> qm1 = ctor.call();

                //read the LOC_KEY value from inside QM
                QuarentineModule.S1<TestQM, Void> readLoc = oconn.resolveInstance(Void.class, TestQM.class, "readLoc");
                readLoc.arg(qm1).call();

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

                //get the TestQM constructor
                ctor = oconn.resolveConstructor(TestQM.class);

                //create the object in the sandbox, and get a ref to it
                Sealed<TestQM> qm1 = ctor.call();

                //get a ref the incrementValue method in TestQM object that was created in the sandbox
                QuarentineModule.S1<TestQM, Void> incrVal = oconn.resolveInstance(void.class, TestQM.class, "incrementValue");

                //call the incrementValue method in TestQM that is loaded in the sandbox
                //all Java methods implicitly take the "this" as the first param. In FlowFence, we
                //pass "this" explicitly.
                incrVal.arg(qm1).call();

                //get the incremented value
                QuarentineModule.S1<TestQM, Integer> getVal = oconn.resolveInstance(int.class, TestQM.class, "getValue");
                Sealed<Integer> retVal = getVal.arg(qm1).call();

                //pass in the value from TestQM into another instance of TestQM
                Sealed<TestQM> qm2 = ctor.call();
                //order is [parameters], return value while declaring ref to QM
                //order is return type, clazz, methodNameAsString, [parameter list] while doing resolution
                QuarentineModule.S2<TestQM, Integer, Void> setValue = oconn.resolveInstance(void.class, TestQM.class, "setValue", int.class);

                setValue.arg(qm2).arg(retVal).call();

            } catch(Exception e)
            {
                Log.i(TAG, "error: " + e);
            }
        }
    }
}

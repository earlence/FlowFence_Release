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

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.apache.commons.lang3.time.StopWatch;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.Sealed;
import edu.umich.flowfence.client.QuarentineModule;
import edu.umich.flowfence.common.FlowfenceConstants;
import edu.umich.flowfence.common.IFlowfenceService;
import edu.umich.flowfence.common.QMDescriptor;

public class TestActivity extends Activity {

    FlowfenceConnection connection;

    QuarentineModule.S0<TestQM> init0;
    QuarentineModule.S1<String, TestQM> init1;

    QuarentineModule.S2<String, String, String> concat;

    QuarentineModule.S1<TestQM, String> getState;
    QuarentineModule.S2<TestQM, String, String> swapState;
    QuarentineModule.S2<TestQM, String, Void> log;
    QuarentineModule.S2<String, String, Void> logStatic;

    QuarentineModule.S1<String, String> throwStatic;
    QuarentineModule.S2<TestQM, String, Void> throwInstance;

    QuarentineModule.S2<String, Boolean, Void> putValueQM;
    QuarentineModule.S0<Void> toastValueQM;
    QuarentineModule.S0<Void> pushValueQM;
    QuarentineModule.S0<Void> toastECUQM;
    QuarentineModule.S0<Void> pushECUQM;
    QuarentineModule.S0<Void> pushOtherQM;

    QuarentineModule.S1<Boolean, Void> nop;

    QuarentineModule.S1<Long, String> sleep;

    EditText kvsValueField;
    CheckBox shouldTaintBox;
    RelativeLayout rootLayout;
    Spinner sandboxCount;
    Spinner sandboxCountType;
    EditText perfPassCount;
    CheckBox taintPerfBox;

    private static final String TAG = "FF.TestActivity";

    public void runTest(View ignored) {
        try {
            setButtonsEnabled(false);
            FlowfenceConnection conn = connection;

            // start resolving
            init0 = conn.resolveConstructor(TestQM.class);
            init1 = conn.resolveConstructor(TestQM.class, String.class);

            concat = conn.resolveStatic(String.class, TestQM.class, "concat",
                                        String.class, String.class);

            getState = conn.resolveInstance(String.class, TestQM.class, "getState");
            swapState = conn.resolveInstance(String.class, TestQM.class, "swapState",
                                             String.class);

            log = conn.resolveInstance(void.class, TestQM.class, "log",
                                       String.class);
            logStatic = conn.resolveStatic(void.class, TestQM.class, "log",
                                           String.class, String.class);

            throwStatic = conn.resolveStatic(String.class, TestQM.class, "throwStatic",
                                             String.class);
            throwInstance = conn.resolveInstance(void.class, TestQM.class, "throwInstance",
                                                 String.class);
            sleep = conn.resolveStatic(String.class, TestQM.class, "sleep",
                                       long.class);
            Log.i(TAG, "Done resolving");

            ComponentName cn = new ComponentName(getPackageName(), "testChannel");
            QMDescriptor sd = logStatic.getDescriptor();
            conn.getRawInterface().subscribeEventChannel(cn, sd);

            Log.i(TAG, "Constructing with init0");
            Sealed<TestQM> obj1 = init0.call();
            obj1.buildCall(log).arg("obj1").call();

            Log.i(TAG, "Constructing with init1");
            Sealed<TestQM> obj2 = init1.arg("<foobar>").call();
            Sealed<String> obj2State = getState.arg(obj2).call();

            logStatic.arg("obj2State").arg(obj2State).call();

            Log.i(TAG, "Calling static method");
            Sealed<String> newState = concat.arg("state").arg(obj2State).call();
            logStatic.arg("newState").arg(newState).call();

            Log.i(TAG, "Calling instance with inout");
            Sealed<String> oldState1 = swapState.inOut(obj1).arg(newState).call();
            logStatic.arg("oldState1").arg(oldState1).call();
            obj1.buildCall(log).arg("obj1'").call();

            Log.i(TAG, "Calling instance without inout");
            Sealed<String> oldState2 = swapState.in(obj2).arg(newState).call();
            logStatic.arg("oldState2").arg(oldState2).call();
            obj2.buildCall(log).arg("obj2'").call();

            Log.i(TAG, "Testing exception propagation - static");
            Sealed<String> excStatic = throwStatic.in("static test #1").call();
            Sealed<String> excStatic2 = throwStatic.in("static test #2").call();
            logStatic.arg(excStatic).arg(excStatic2).call();

            Log.i(TAG, "Testing exception propagation - instance");

            obj1.buildCall(throwInstance).arg("instance test - obj1").call();
            obj1.buildCall(log).argNull().call();

            throwInstance.in(obj2).arg("instance test - obj2").call();
            obj2.buildCall(log).argNull().call();

            Log.i(TAG, "Testing async method calls");
            Sealed<String> sleepResult = sleep.arg(15 * 1000L).asAsync().call();
            logStatic.arg("Sleep result").arg(sleepResult).asAsync().call();

            Log.i(TAG, "All done!");

        } catch (Exception e) {
            Log.wtf(TAG, "Exception in post-connect handler", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void putValue(View ignored) {
        try {
            setButtonsEnabled(false);

            if (putValueQM == null) {
                putValueQM = connection.resolveStatic(void.class, KeyValueTest.class,
                                                        "setValue", String.class, boolean.class);
            }

            putValueQM.arg(kvsValueField.getText().toString())
                        .arg(shouldTaintBox.isChecked())
                        .call();

        } catch (Exception e) {
            Log.wtf(TAG, "Exception in putValue()", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void toastValue(View ignored) {
        try {
            setButtonsEnabled(false);

            if (toastValueQM == null) {
                toastValueQM = connection.resolveStatic(void.class, KeyValueTest.class,
                                                          "toastValue");
            }

            toastValueQM.call();
        } catch (Exception e) {
            Log.wtf(TAG, "Exception in toastValue()", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void pushValue(View ignored) {
        try {
            setButtonsEnabled(false);

            if (pushValueQM == null) {
                pushValueQM = connection.resolveStatic(void.class, KeyValueTest.class,
                                                         "pushValue");
            }

            pushValueQM.call();
        } catch (Exception e) {
            Log.wtf(TAG, "Exception in pushValue()", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void toastECU(View ignored) {
        try {
            setButtonsEnabled(false);

            if (toastECUQM == null) {
                toastECUQM = connection.resolveStatic(void.class, GMTest.class,
                                                        "toastValue");
            }

            toastECUQM.call();
        } catch (Exception e) {
            Log.wtf(TAG, "Exception in toastECU()", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void pushECU(View ignored) {
        try {
            setButtonsEnabled(false);

            if (pushECUQM == null) {
                pushECUQM = connection.resolveStatic(void.class, GMTest.class,
                                                       "pushValue");
            }

            pushECUQM.call();
        } catch (Exception e) {
            Log.wtf(TAG, "Exception in pushECU()", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void pushOther(View ignored) {
        try {
            setButtonsEnabled(false);

            if (pushOtherQM == null) {
                pushOtherQM = connection.resolveStatic(void.class, GMTest.class,
                                                         "pushNonValue");
            }

            pushOtherQM.call();
        } catch (Exception e) {
            Log.wtf(TAG, "Exception in pushOther()", e);
        } finally {
            Log.i(TAG, "Finishing");
            setButtonsEnabled(true);
        }
    }

    public void setSandboxCount(View v) {
        int numSandboxes = sandboxCount.getSelectedItemPosition();
        int countTypeId = sandboxCountType.getSelectedItemPosition();
        String countType = (String)sandboxCountType.getSelectedItem();
        try {
            int oldCount;
            if (countTypeId == 0) { // total sandboxes
                oldCount = connection.getRawInterface().setSandboxCount(numSandboxes);

                // Run nop in each sandbox to ensure code is loaded.
                for (int i = oldCount; i < numSandboxes; i++) {
                    getNop().arg(false).forceSandbox(i).call();
                }
            } else if (countTypeId == 1) {
                oldCount = connection.getRawInterface().setMaxIdleCount(numSandboxes);
            } else if (countTypeId == 2) {
                oldCount = connection.getRawInterface().setMinHotSpare(numSandboxes);
            } else if (countTypeId == 3) {
                oldCount = connection.getRawInterface().setMaxHotSpare(numSandboxes);
            } else {
                throw new IllegalStateException("No idea how to set " + countType);
            }

            Toast.makeText(this,
                           String.format("%s changed from %d to %d",
                                         countType,
                                         oldCount,
                                         numSandboxes),
                           Toast.LENGTH_LONG).show();
        } catch (RemoteException | ClassNotFoundException | IllegalStateException e) {
            Toast.makeText(this, "Set count failed: " + e, Toast.LENGTH_LONG).show();
        }
    }

    public void doPerfTest(View ignored) {
        LatencyTestTask task = new LatencyTestTask();
        int numPasses = Integer.parseInt(perfPassCount.getText().toString());
        task.execute(numPasses);
    }

    public void connectToFlowfence(View ignored) {
        Log.i(TAG, "Binding to FlowFence...");
        FlowfenceConnection.bind(this, new FlowfenceConnection.DisconnectCallback() {
            @Override
            public void onConnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Bound to FlowFence");
                onFlowfenceConnectStateChange(conn);
            }

            @Override
            public void onDisconnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Unbound from FlowFence");
                onFlowfenceConnectStateChange(null);
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        int childCount = rootLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = rootLayout.getChildAt(i);
            if (v instanceof Button &&
                    v.getId() != R.id.reconnect &&
                    v.getId() != R.id.perf_tests) {
                v.setEnabled(enabled);
            }
        }
    }

    private void onFlowfenceConnectStateChange(FlowfenceConnection conn) {
        connection = conn;
        putValueQM = null;
        toastValueQM = null;
        pushValueQM = null;
        toastECUQM = null;
        pushECUQM = null;
        pushOtherQM = null;
        nop = null;
        setButtonsEnabled(conn != null);
    }

    private synchronized QuarentineModule.S1<Boolean, Void> getNop()
            throws RemoteException, ClassNotFoundException {
        if (nop == null) {
            nop = connection.resolveStatic(void.class, TestQM.class, "nop", boolean.class);
        }
        return nop;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        kvsValueField = (EditText)findViewById(R.id.kvs_value);
        shouldTaintBox = (CheckBox)findViewById(R.id.is_tainted);
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);
        sandboxCount = (Spinner)findViewById(R.id.sandbox_count);
        sandboxCountType = (Spinner)findViewById(R.id.sandbox_count_type);
        perfPassCount = (EditText)findViewById(R.id.perf_pass_count);
        taintPerfBox = (CheckBox)findViewById(R.id.perf_taint);

        // Set up adapter for sandbox count spinner
        CharSequence[] countList = new CharSequence[FlowfenceConstants.NUM_SANDBOXES + 1];
        for (int i = 0; i <= FlowfenceConstants.NUM_SANDBOXES; i++) {
            countList[i] = getResources().getQuantityString(R.plurals.sandbox_plurals, i, i);
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this,
                                                                android.R.layout.simple_spinner_item,
                                                                countList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sandboxCount.setAdapter(adapter);
        sandboxCount.setSelection(FlowfenceConstants.NUM_SANDBOXES);

        adapter = ArrayAdapter.createFromResource(this, R.array.sandbox_count_labels,
                                                  android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sandboxCountType.setAdapter(adapter);

        setButtonsEnabled(false);
        connectToFlowfence(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_test, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void launchPerfTests(View v) {
        startActivity(new Intent(this, PerfActivity.class));
        finish();
    }

    private class LatencyTestTask extends AsyncTask<Integer, Void, String> {
        private boolean shouldTaint;

        @Override
        protected void onPreExecute() {
            setButtonsEnabled(false);
            shouldTaint = taintPerfBox.isChecked();
        }

        @Override
        protected void onPostExecute(String result) {
            setButtonsEnabled(true);
            Notification notification = new Notification.Builder(TestActivity.this)
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .setContentTitle("Perf run complete")
                    .setContentText(result)
                    .build();
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(0, notification);
        }

        @Override
        protected void onCancelled() {
            setButtonsEnabled(true);
        }

        @Override
        protected String doInBackground(Integer... params) {
            IFlowfenceService svc = connection.getRawInterface();
            int numPasses = params[0];
            try {
                QuarentineModule.S1<Boolean, Void> nop = getNop();
                StopWatch stopwatch = new StopWatch();
                nop.arg(shouldTaint).call();


                stopwatch.start();
                for (int i = 0; i < numPasses; i++) {
                    nop.arg(shouldTaint).call();
                }
                stopwatch.stop();

                return String.format("Completed %d calls in %s", numPasses, stopwatch);
            } catch (Exception e) {
                Log.e("TestActivity", "Latency test failed", e);
                return "Test failed: " + e;
            }
        }
    }
}

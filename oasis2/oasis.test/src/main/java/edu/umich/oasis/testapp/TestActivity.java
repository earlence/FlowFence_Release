package edu.umich.oasis.testapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
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

import edu.umich.oasis.client.CallRunner;
import edu.umich.oasis.client.OASISConnection;
import edu.umich.oasis.client.Sealed;
import edu.umich.oasis.client.Soda;
import edu.umich.oasis.common.IOASISService;
import edu.umich.oasis.common.OASISConstants;
import edu.umich.oasis.common.ResolveFlags;
import edu.umich.oasis.common.SodaDescriptor;

public class TestActivity extends Activity {

    OASISConnection connection;

    Soda.S0<TestSoda> init0;
    Soda.S1<String, TestSoda> init1;

    Soda.S2<String, String, String> concat;

    Soda.S1<TestSoda, String> getState;
    Soda.S2<TestSoda, String, String> swapState;
    Soda.S2<TestSoda, String, Void> log;
    Soda.S2<String, String, Void> logStatic;

    Soda.S1<String, String> throwStatic;
    Soda.S2<TestSoda, String, Void> throwInstance;

    Soda.S2<String, Boolean, Void> putValueSoda;
    Soda.S0<Void> toastValueSoda;
    Soda.S0<Void> pushValueSoda;
    Soda.S0<Void> toastECUSoda;
    Soda.S0<Void> pushECUSoda;
    Soda.S0<Void> pushOtherSoda;

    Soda.S1<Boolean, Void> nop;

    Soda.S1<Long, String> sleep;

    EditText kvsValueField;
    CheckBox shouldTaintBox;
    RelativeLayout rootLayout;
    Spinner sandboxCount;
    Spinner sandboxCountType;
    EditText perfPassCount;
    CheckBox taintPerfBox;

    private static final String TAG = "OASIS.TestActivity";

    public void runTest(View ignored) {
        try {
            setButtonsEnabled(false);
            OASISConnection conn = connection;

            // start resolving
            init0 = conn.resolveConstructor(TestSoda.class);
            init1 = conn.resolveConstructor(TestSoda.class, String.class);

            concat = conn.resolveStatic(String.class, TestSoda.class, "concat",
                                        String.class, String.class);

            getState = conn.resolveInstance(String.class, TestSoda.class, "getState");
            swapState = conn.resolveInstance(String.class, TestSoda.class, "swapState",
                                             String.class);

            log = conn.resolveInstance(void.class, TestSoda.class, "log",
                                       String.class);
            logStatic = conn.resolveStatic(void.class, TestSoda.class, "log",
                                           String.class, String.class);

            throwStatic = conn.resolveStatic(String.class, TestSoda.class, "throwStatic",
                                             String.class);
            throwInstance = conn.resolveInstance(void.class, TestSoda.class, "throwInstance",
                                                 String.class);
            sleep = conn.resolveStatic(String.class, TestSoda.class, "sleep",
                                       long.class);
            Log.i(TAG, "Done resolving");

            ComponentName cn = new ComponentName(getPackageName(), "testChannel");
            SodaDescriptor sd = logStatic.getDescriptor();
            conn.getRawInterface().subscribeEventChannel(cn, sd);

            Log.i(TAG, "Constructing with init0");
            Sealed<TestSoda> obj1 = init0.call();
            obj1.buildCall(log).arg("obj1").call();

            Log.i(TAG, "Constructing with init1");
            Sealed<TestSoda> obj2 = init1.arg("<foobar>").call();
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

            if (putValueSoda == null) {
                putValueSoda = connection.resolveStatic(void.class, KeyValueTest.class,
                                                        "setValue", String.class, boolean.class);
            }

            putValueSoda.arg(kvsValueField.getText().toString())
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

            if (toastValueSoda == null) {
                toastValueSoda = connection.resolveStatic(void.class, KeyValueTest.class,
                                                          "toastValue");
            }

            toastValueSoda.call();
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

            if (pushValueSoda == null) {
                pushValueSoda = connection.resolveStatic(void.class, KeyValueTest.class,
                                                         "pushValue");
            }

            pushValueSoda.call();
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

            if (toastECUSoda == null) {
                toastECUSoda = connection.resolveStatic(void.class, GMTest.class,
                                                        "toastValue");
            }

            toastECUSoda.call();
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

            if (pushECUSoda == null) {
                pushECUSoda = connection.resolveStatic(void.class, GMTest.class,
                                                       "pushValue");
            }

            pushECUSoda.call();
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

            if (pushOtherSoda == null) {
                pushOtherSoda = connection.resolveStatic(void.class, GMTest.class,
                                                         "pushNonValue");
            }

            pushOtherSoda.call();
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

    public void connectToOASIS(View ignored) {
        Log.i(TAG, "Binding to OASIS...");
        OASISConnection.bind(this, new OASISConnection.DisconnectCallback() {
            @Override
            public void onConnect(OASISConnection conn) throws Exception {
                Log.i(TAG, "Bound to OASIS");
                onOASISConnectStateChange(conn);
            }

            @Override
            public void onDisconnect(OASISConnection conn) throws Exception {
                Log.i(TAG, "Unbound from OASIS");
                onOASISConnectStateChange(null);
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

    private void onOASISConnectStateChange(OASISConnection conn) {
        connection = conn;
        putValueSoda = null;
        toastValueSoda = null;
        pushValueSoda = null;
        toastECUSoda = null;
        pushECUSoda = null;
        pushOtherSoda = null;
        nop = null;
        setButtonsEnabled(conn != null);
    }

    private synchronized Soda.S1<Boolean, Void> getNop()
            throws RemoteException, ClassNotFoundException {
        if (nop == null) {
            nop = connection.resolveStatic(void.class, TestSoda.class, "nop", boolean.class);
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
        CharSequence[] countList = new CharSequence[OASISConstants.NUM_SANDBOXES + 1];
        for (int i = 0; i <= OASISConstants.NUM_SANDBOXES; i++) {
            countList[i] = getResources().getQuantityString(R.plurals.sandbox_plurals, i, i);
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this,
                                                                android.R.layout.simple_spinner_item,
                                                                countList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sandboxCount.setAdapter(adapter);
        sandboxCount.setSelection(OASISConstants.NUM_SANDBOXES);

        adapter = ArrayAdapter.createFromResource(this, R.array.sandbox_count_labels,
                                                  android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sandboxCountType.setAdapter(adapter);

        setButtonsEnabled(false);
        connectToOASIS(null);
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
            IOASISService svc = connection.getRawInterface();
            int numPasses = params[0];
            try {
                Soda.S1<Boolean, Void> nop = getNop();
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

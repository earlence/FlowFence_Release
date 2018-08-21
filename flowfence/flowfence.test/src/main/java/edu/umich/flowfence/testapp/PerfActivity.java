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
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Message;
import android.os.OperationCanceledException;
import android.os.PowerManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayout;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.apache.commons.lang3.time.StopWatch;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.QuarentineModule;
import edu.umich.flowfence.common.IFlowfenceService;
import edu.umich.flowfence.common.FlowfenceConstants;

public class PerfActivity extends Activity implements CompoundButton.OnCheckedChangeListener,
                                                      View.OnClickListener,
                                                      FlowfenceConnection.DisconnectCallback {

    private static final String TAG = "FF.PerfTest";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private ScrollView paramsView;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button executeButton;

    private Switch latencySwitch;
    private GridLayout latencyGrid;
    private EditText latencyLoops;
    private EditText latencyTrials;
    private CheckBox latencyTainted;
    private CheckBox latencyUntainted;
    private EditText latencySparesLow;
    private EditText latencySparesHigh;

    private Switch marshalSwitch;
    private GridLayout marshalGrid;
    private EditText marshalLoops;
    private EditText marshalTrials;
    private EditText marshalSizes;

    private Switch memorySwitch;
    private GridLayout memoryGrid;
    private EditText memorySandboxesMin;
    private EditText memorySandboxesMax;

    private FlowfenceConnection conn;
    private ServiceConnection sc;
    private PerfTask task;

    private Resources res;
    private QuarentineModule.S2<Boolean, byte[], Void> execQM;

    public static int getClampedSandboxCount(EditText text) {
        int count = Integer.parseInt(text.getText().toString());
        return Math.max(0, Math.min(count, FlowfenceConstants.NUM_SANDBOXES));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perf);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        paramsView = (ScrollView)findViewById(R.id.perf_params);
        progressBar = (ProgressBar)findViewById(R.id.perf_progress);
        statusText = (TextView)findViewById(R.id.perf_status);
        executeButton = (Button)findViewById(R.id.perf_execute);

        latencySwitch = (Switch)findViewById(R.id.perf_do_latency);
        latencyGrid = (GridLayout)findViewById(R.id.perf_params_latency);
        latencyLoops = (EditText)findViewById(R.id.perf_latency_loops_per_trial);
        latencyTrials = (EditText)findViewById(R.id.perf_latency_trials_per_run);
        latencyTainted = (CheckBox)findViewById(R.id.perf_latency_tainted);
        latencyUntainted = (CheckBox)findViewById(R.id.perf_latency_not_tainted);
        latencySparesLow = (EditText)findViewById(R.id.perf_latency_spares_low);
        latencySparesHigh = (EditText)findViewById(R.id.perf_latency_spares_high);

        marshalSwitch = (Switch)findViewById(R.id.perf_do_marshal);
        marshalGrid = (GridLayout)findViewById(R.id.perf_params_marshal);
        marshalLoops = (EditText)findViewById(R.id.perf_marshal_loops_per_trial);
        marshalTrials = (EditText)findViewById(R.id.perf_marshal_trials_per_run);
        marshalSizes = (EditText)findViewById(R.id.perf_marshal_sizes);

        memorySwitch = (Switch)findViewById(R.id.perf_do_memory);
        memoryGrid = (GridLayout)findViewById(R.id.perf_params_memory);
        memorySandboxesMin = (EditText)findViewById(R.id.perf_memory_sandboxes_low);
        memorySandboxesMax = (EditText)findViewById(R.id.perf_memory_sandboxes_high);

        latencySwitch.setTag(R.id.perf_tests, latencyGrid);
        latencySwitch.setOnCheckedChangeListener(this);
        marshalSwitch.setTag(R.id.perf_tests, marshalGrid);
        marshalSwitch.setOnCheckedChangeListener(this);
        memorySwitch.setTag(R.id.perf_tests, memoryGrid);
        memorySwitch.setOnCheckedChangeListener(this);

        executeButton.setOnClickListener(this);
        res = getResources();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        GridLayout layout = (GridLayout)buttonView.getTag(R.id.perf_tests);
        layout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View v) {
        if (task == null) {
            task = new PerfTask();
            FlowfenceConnection.bind(this, this);
            paramsView.setEnabled(false);
            executeButton.setText(R.string.perf_cancel);
        } else {
            task.cancel(false);
            paramsView.setEnabled(true);
            task = null;
            executeButton.setText(R.string.perf_start);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void onEndTask(int text) {
        paramsView.setEnabled(true);
        statusText.setText(text);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(1);
        progressBar.setMax(1);
        if (this.conn != null) {
            this.conn.close();
        }
        executeButton.setText(R.string.perf_start);
        this.task = null;
    }

    private void showAlert(Throwable t) {
        if (t != null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.perf_failed_title)
                    .setMessage(Log.getStackTraceString(t))
                    .show();
        }
    }

    @Override
    public void onConnect(FlowfenceConnection conn) throws Exception {
        this.conn = conn;
        if (this.task != null) {
            try {
                execQM = conn.resolveStatic(void.class, PerfQM.class, "execQM",
                                              boolean.class, byte[].class);
                task.execute(
                        memorySwitch.isChecked() ? new MemoryTest() : null,
                        latencySwitch.isChecked() ? new LatencyTest() : null,
                        marshalSwitch.isChecked() ? new MarshalTest() : null);
            } catch (Exception e) {
                showAlert(e);
                onEndTask(R.string.task_failed);
            }
        }
    }

    @Override
    public void onDisconnect(FlowfenceConnection conn) throws Exception {
        this.conn = null;
    }

    private final class LatencyTest extends PerfSubtest {
        private final int loops, trials, sandboxesLow, sandboxesHigh;
        private final boolean tainted, untainted;
        private IFlowfenceService svc;

        public LatencyTest() {
            super("LatencyTest");
            tainted = latencyTainted.isChecked();
            untainted = latencyUntainted.isChecked();
            if (!tainted && !untainted) {
                throw new IllegalArgumentException("Must specify one type of test to run");
            }

            loops = Integer.parseInt(latencyLoops.getText().toString());
            trials = Integer.parseInt(latencyTrials.getText().toString());
            sandboxesLow = getClampedSandboxCount(latencySparesLow);
            sandboxesHigh = getClampedSandboxCount(latencySparesHigh);
        }

        private long executeTrial(PerfTask task, boolean shouldTaint, int numSpares) throws Exception {
            StopWatch stopWatch = new StopWatch();

            // Reset to a known state.
            svc.setMinHotSpare(numSpares);
            svc.setSandboxCount(0);
            svc.setSandboxCount(FlowfenceConstants.NUM_SANDBOXES);

            // Get all of the sandboxes into steady state.
            for (int i = 0; i < FlowfenceConstants.NUM_SANDBOXES; i++) {
                execQM.arg(shouldTaint).argNull().call();
            }

            svc.forceGarbageCollection();

            stopWatch.start();
            for (int i = 0; i < loops; i++) {
                execQM.arg(shouldTaint).argNull().call();
                task.throwIfCancelled();
            }
            stopWatch.stop();

            return stopWatch.getNanoTime();
        }

        private void doTrials(PerfTask task, PrintWriter writer, boolean shouldTaint) throws Exception {
            int progress = 0;
            final int totalProgress = (sandboxesHigh - sandboxesLow + 1) * trials;
            for (int numSpares = sandboxesLow; numSpares <= sandboxesHigh; numSpares++) {
                for (int trial = 1; trial <= trials; trial++) {
                    task.throwIfCancelled();
                    String status = String.format("%s: Testing %s, %d/%d sandboxes, trial %d/%d",
                                                  describe(), shouldTaint ? "tainted" : "untainted",
                                                  numSpares, sandboxesHigh, trial, trials);

                    task.publishProgress(progress++, totalProgress, status);

                    long totalTimeNanos = executeTrial(task, shouldTaint, numSpares);
                    long averageTimeNanos = totalTimeNanos / loops;
                    writer.format("%b,%d,%d,%d,%d,%d", shouldTaint, numSpares, trial, loops,
                                  totalTimeNanos, averageTimeNanos);
                    writer.println();
                }
            }
        }

        @Override
        public void execute(PerfTask task) throws Exception {
            task.publishProgress(-1, -1, describe()+": Initializing...");
            svc = conn.getRawInterface();

            final int oldSandboxCount = svc.setSandboxCount(/*0*/FlowfenceConstants.NUM_SANDBOXES);
            final int oldMinSpare = svc.setMinHotSpare(0);
            final int oldMaxSpare = svc.setMaxHotSpare(FlowfenceConstants.NUM_SANDBOXES);
            final int oldMaxIdle = svc.setMaxIdleCount(FlowfenceConstants.NUM_SANDBOXES);

            try (PrintWriter out = new PrintWriter(openRunOutput("csv"), true)) {
                out.println("Tainted,Number of Spares,Trial,Loops,Total Latency (ns),Average Latency (ns)");

                if (untainted) {
                    doTrials(task, out, false);
                }

                if (tainted) {
                    doTrials(task, out, true);
                }

            } finally {
                svc.setSandboxCount(oldSandboxCount);
                svc.setMaxHotSpare(oldMaxSpare);
                svc.setMinHotSpare(oldMinSpare);
                svc.setMaxIdleCount(oldMaxIdle);
            }
        }
    }

    private final class MarshalTest extends PerfSubtest {
        private final SparseArray<String> sizes;
        private final int loops, trials;

        private int parseSize(String size) {
            char suffix = Character.toUpperCase(size.charAt(size.length()-1));
            int multiplier;
            switch (suffix) {
                case 'B':
                    multiplier = 1;
                    break;
                case 'K':
                    multiplier = 1024;
                    break;
                case 'M':
                    multiplier = 1024*1024;
                    break;
                case 'G':
                    multiplier = 1024*1024*1024;
                    break;
                default:
                    return Integer.parseInt(size);
            }
            final int coefficient = Integer.parseInt(size.substring(0, size.length()-1).trim());
            return coefficient * multiplier;
        }

        public MarshalTest() {
            super("MarshalTest");
            loops = Integer.parseInt(marshalLoops.getText().toString());
            trials = Integer.parseInt(marshalTrials.getText().toString());

            String marshalSizeString = marshalSizes.getText().toString();

            String[] humanSizes = marshalSizeString.split(",\\s*");

            sizes = new SparseArray<>(humanSizes.length);

            for (String humanSize : humanSizes) {
                int size = parseSize(humanSize);
                sizes.append(size, humanSize);
            }
        }

        private final long NANOS_PER_SEC = (1000L*1000L*1000L);

        public void execute(PerfTask task) throws Exception {
            final StopWatch stopWatch = new StopWatch();
            final IFlowfenceService svc = conn.getRawInterface();
            final byte[] emptyByteArray = new byte[0];
            try (FileInputStream urandom = new FileInputStream("/dev/urandom");
                 PrintWriter out = new PrintWriter(openRunOutput("csv"), true)) {
                out.println("Data Size,Trial,Loops,Total Latency (ns),Average Latency (ns),Average Bandwidth (bytes/s)");

                final int numSizes = sizes.size();
                final int totalProgress = numSizes * trials;
                int progress = 0;

                for (int offset = 0; offset < numSizes; offset++) {
                    final int size = sizes.keyAt(offset);
                    final String humanSize = sizes.valueAt(offset);
                    task.throwIfCancelled();
                    task.publishProgress(-1, -1, describe()+": Generating "+humanSize+" bytes");

                    final byte[] buf = new byte[size];
                    urandom.read(buf);

                    for (int trial = 1; trial <= trials; trial++) {
                        task.publishProgress(progress++, totalProgress,
                                             String.format("%s: Running %s trial %d/%d",
                                                           describe(), humanSize, trial, trials));

                        int oldSandboxes = svc.setSandboxCount(0);
                        svc.setSandboxCount(oldSandboxes);

                        execQM.arg(false).arg(emptyByteArray).call();
                        svc.forceGarbageCollection();

                        stopWatch.start();
                        for (int loop = 0; loop < loops; loop++) {
                            execQM.arg(false).arg(buf).call();
                        }
                        stopWatch.stop();

                        long totalTimeNanos = stopWatch.getNanoTime();
                        long averageTimeNanos = totalTimeNanos / loops;
                        long bandwidth = (size * loops * NANOS_PER_SEC) / totalTimeNanos;

                        out.format("%s,%d,%d,%d,%d,%d", humanSize, trial, loops, totalTimeNanos,
                                   averageTimeNanos, bandwidth)
                           .println();

                        stopWatch.reset();
                    }
                }
            }
        }
    }

    private final class MemoryTest extends PerfSubtest {
        final int minSandboxes, maxSandboxes;

        public MemoryTest() {
            super("MemoryTest");
            minSandboxes = getClampedSandboxCount(memorySandboxesMin);
            maxSandboxes = getClampedSandboxCount(memorySandboxesMax);
        }

        public void execute(PerfTask task) throws Exception {
            task.publishProgress(-1, -1, describe()+": Initializing...");
            IFlowfenceService svc = conn.getRawInterface();
            List<Debug.MemoryInfo> sandboxMemInfo = new ArrayList<>(FlowfenceConstants.NUM_SANDBOXES);
            Debug.MemoryInfo serviceMemInfo;
            final int oldSandboxCount = svc.setSandboxCount(0);
            final int oldMinSpare = svc.setMinHotSpare(0);
            final int oldMaxSpare = svc.setMaxHotSpare(0);
            final int oldMaxIdle = svc.setMaxIdleCount(FlowfenceConstants.NUM_SANDBOXES);

            try (PrintWriter out = new PrintWriter(openRunOutput("csv"), true)) {
                out.println("Number of Sandboxes,Trusted Service PSS,Sandboxes PSS,Total PSS");
                for (int sbCount = minSandboxes; sbCount <= maxSandboxes; sbCount++) {
                    task.throwIfCancelled();
                    task.publishProgress(sbCount - minSandboxes,
                                         maxSandboxes - minSandboxes + 1,
                                         describe()+": Getting memory info for " + sbCount + " sandboxes");

                    svc.setSandboxCount(0);
                    svc.setSandboxCount(sbCount);

                    for (int i = 0; i < sbCount; i++) {
                        execQM.arg(false)
                                .argNull()
                                .forceSandbox(i)
                                .call();
                    }

                    svc.forceGarbageCollection();

                    sandboxMemInfo.clear();
                    serviceMemInfo = svc.dumpMemoryInfo(sandboxMemInfo);

                    int servicePss = serviceMemInfo.getTotalPss();
                    int sandboxPss = 0;
                    for (Debug.MemoryInfo sbInfo : sandboxMemInfo) {
                        if (sbInfo != null) {
                            sandboxPss += sbInfo.getTotalPss();
                        }
                    }

                    Log.d(TAG,
                          String.format("%d sandboxes, service %d, sandboxes %d",
                                        sbCount,
                                        servicePss,
                                        sandboxPss));

                    out.format("%d,%d,%d,%d",
                               sbCount,
                               servicePss,
                               sandboxPss,
                               servicePss + sandboxPss)
                       .println();
                }
            } finally {
                svc.setSandboxCount(oldSandboxCount);
                svc.setMaxHotSpare(oldMaxSpare);
                svc.setMinHotSpare(oldMinSpare);
                svc.setMaxIdleCount(oldMaxIdle);
            }
        }
    }

    private abstract class PerfSubtest {
        private final String type;
        protected PerfSubtest(String type) {
            this.type = Objects.requireNonNull(type);
        }

        public String describe() {
            return type;
        }

        private File getOutputFile(String extension) {
            String fileName = res.getString(R.string.perf_result_filename,
                                            type,
                                            System.currentTimeMillis(),
                                            extension);

            File file = new File(getExternalFilesDir(null), fileName);
            file.getParentFile().mkdirs();
            return file;
        }

        protected OutputStream openRunOutput(String extension) throws IOException {
            return new BufferedOutputStream(new FileOutputStream(getOutputFile(extension), false));
        }

        public abstract void execute(PerfTask task) throws Exception;
    }

    private final class PerfTask extends AsyncTask<PerfSubtest, Message, Throwable> {
        public final void publishProgress(int amountDone, int totalAmount, CharSequence status) {
            if (status == null) {
                status = "";
            }
            publishProgress(Message.obtain(null, amountDone, totalAmount, -1, status));
        }

        public final void publishProgress(int amountDone, int totalAmount, int statusTextId) {
            publishProgress(Message.obtain(null, amountDone, totalAmount, statusTextId, null));
        }

        @Override
        protected final void onPreExecute() {
            res = getResources();
            paramsView.setEnabled(false);
            statusText.setText(R.string.task_starting);
            progressBar.setIndeterminate(true);
        }

        @Override
        protected final void onPostExecute(Throwable result) {
            if (result == null) {
                onEndTask(R.string.task_complete);
            } else {
                onEndTask(R.string.task_failed);
                new AlertDialog.Builder(PerfActivity.this)
                        .setTitle(R.string.perf_failed_title)
                        .setMessage(Log.getStackTraceString(result))
                        .show();
            }
        }

        @Override
        protected final void onProgressUpdate(Message... values) {
            Message msg = values[0];
            int progressAmount = msg.what;
            int totalAmount = msg.arg1;
            int resId = msg.arg2;
            CharSequence status = (CharSequence)msg.obj;

            if (progressAmount == -1) {
                progressBar.setIndeterminate(true);
            } else {
                progressBar.setIndeterminate(false);
                progressBar.setMax(totalAmount);
                progressBar.setProgress(progressAmount);
            }

            if (resId != -1) {
                statusText.setText(resId);
            } else {
                statusText.setText(status);
            }

            msg.recycle();
        }

        @Override
        protected final void onCancelled(Throwable unused) {
            onEndTask(R.string.task_cancelled);
        }

        @Override
        protected final Throwable doInBackground(PerfSubtest... params) {
            final PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
            final PowerManager.WakeLock lock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            try {
                lock.acquire();
                for (PerfSubtest test : params) {
                    if (test == null) {
                        continue;
                    }

                    String description = test.describe();
                    String status = res.getString(R.string.task_starting_task, description);
                    publishProgress(-1, -1, status);

                    try {
                        test.execute(this);
                    } catch (OperationCanceledException oce) {
                        Log.i(TAG, "Cancelled", oce);
                        return oce;
                    } catch (Throwable th) {
                        Log.e(TAG, "Error when executing task " + description, th);
                        return th;
                    }
                }

                return null;
            } finally {
                if (lock.isHeld()) {
                    lock.release();
                }
            }
        }

        public final boolean isTaskCancelled() {
            return super.isCancelled();
        }

        public final void throwIfCancelled() throws OperationCanceledException {
            if (super.isCancelled()) {
                throw new OperationCanceledException();
            }
        }
    }
}

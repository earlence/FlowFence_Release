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

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import org.apache.commons.lang3.StringUtils;
import java.util.Objects;
import edu.umich.flowfence.common.IEventChannelAPI;
import edu.umich.flowfence.common.IDynamicAPI;
import edu.umich.flowfence.common.FlowfenceContext;
import edu.umich.flowfence.common.TaintSet;

public class TestQM implements Parcelable {

    //how to talk to trusted service
    public static void nop(boolean addTaint) {
        if (addTaint) {
            TaintSet.Builder ts = new TaintSet.Builder();
            ts.addTaint("edu.umich.flowfence.testapp/test");
            Log.v(TAG, "Tainting");
            ((IDynamicAPI)(FlowfenceContext.getInstance().getTrustedAPI("taint")))
                    .invoke("addTaint", ts.build());
        }
    }

    public static final class TestException extends RuntimeException {
        public TestException(String message) {
            super(message);
        }
    }

    private static final String TAG = "FF.TestQM";

    private static void trace(String method, Object... args) {
        //Log.i(TAG, String.format("%s()", method));
        Log.i(TAG, String.format("%s(%s)", method, StringUtils.join(args, ", ")));
    }

    public static String concat(String left, String right) {
        trace("concat", left, right);
        return String.format("['%s', '%s']", left, right);
    }

    private String state;

    private final void init(String state) {
        this.state = state;
    }

    public TestQM() {
        trace("<init>");
        init("foo");
    }

    public TestQM(String state) {
        trace("<init>", state);
        init(state);
    }

    public String getState() {
        trace("getState", this);
        IEventChannelAPI eventApi = (IEventChannelAPI) FlowfenceContext.getInstance().getTrustedAPI("event");
        eventApi.fireEvent(ComponentName.unflattenFromString("edu.umich.flowfence.testapp/testChannel"), "Channel Test", state);
        return state;
    }

    public String swapState(String newState) {
        trace("swapState", this, newState);
        String oldState = state;
        state = newState;
        return oldState;
    }

    @Override
    public String toString() {
        return String.format("TestQM(%s)", (state.length() > 512 ? state.length() : state));
    }

    private static void logCommon(String name, Object result) {
        String resultString = Objects.toString(result, "<null>");
        if (name == null) {
            Log.i(TAG, String.format("log: [%s]", resultString));
        } else {
            Log.i(TAG, String.format("log: %s = [%s]", name, resultString));
        }
    }

    public void log(String name) {
        logCommon(name, this);
    }

    public static void log(String name, String result) {
        logCommon(name, result);
    }

    //region Parcelable implementation
    public TestQM(Parcel source, ClassLoader loader) {
        init(source.readString());
        trace("parcel.in", this);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(state);
        trace("parcel.out", this);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TestQM> CREATOR
            = new ClassLoaderCreator<TestQM>() {
        @Override
        public TestQM createFromParcel(Parcel source) {
            return createFromParcel(source, getClass().getClassLoader());
        }


        @Override
        public TestQM createFromParcel(Parcel source, ClassLoader loader) {
            return new TestQM(source, loader);
        }

        @Override
        public TestQM[] newArray(int size) {
            return new TestQM[size];
        }
    };
    //endregion

    @Override
    protected void finalize() throws Throwable {
        trace("finalize", this);
        super.finalize();
    }

    public static void collect() {
        System.gc();
    }

    public static String sleep(long millis) {
        long startNanos = SystemClock.elapsedRealtimeNanos();
        SystemClock.sleep(millis);
        long endNanos = SystemClock.elapsedRealtimeNanos();
        return endNanos - startNanos + " ns";
    }

    public static String throwStatic(String message) {
        throw new TestException(message);
    }

    public void throwInstance(String message) {
        throw new TestException(message);
    }
}

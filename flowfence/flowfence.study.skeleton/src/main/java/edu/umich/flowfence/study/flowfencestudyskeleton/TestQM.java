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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import edu.umich.flowfence.common.FlowfenceContext;

/**
 * Created by earlence on 1/18/16.
 */
public class TestQM implements Parcelable {

    //QM state
    private int mValue;

    //non-state
    private static final String TAG = "earlence/TestQM";

    public TestQM()
    {
        mValue = 0;
        Log.i(TAG, "TestQM ctor");
    }

    public void incrementValue()
    {
        mValue += 1;
        Log.i(TAG, "new mValue: " + mValue);
    }

    public int getValue()
    {
        return mValue;
    }

    public void setValue(Integer newval)
    {
        mValue = newval;
        Log.i(TAG, "mValue is: " + mValue);
    }

    public void readLoc()
    {
        SharedPreferences myprefs = FlowfenceContext.getInstance().getSharedPreferences(MainActivity.STORE_NAME, Context.MODE_WORLD_READABLE);
        Log.i(TAG, "LOC_KEY value is: " + myprefs.getString(MainActivity.LOC_KEY, "null"));
    }

    public void putLoc(String val)
    {
        //get a KV store that is world readable
        SharedPreferences myprefs = FlowfenceContext.getInstance().getSharedPreferences(MainActivity.STORE_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor edit = myprefs.edit();

        edit.putString(MainActivity.LOC_KEY, val);
        edit.commit();
    }

    //boiler-plate parcel serialize/deserialize
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(mValue);
    }

    public static final Parcelable.Creator<TestQM> CREATOR = new Parcelable.Creator<TestQM>()
    {
        public TestQM createFromParcel(Parcel in) {
            return new TestQM(in);
        }

        public TestQM[] newArray(int size) {
            return new TestQM[size];
        }
    };

    private TestQM(Parcel in)
    {
        mValue = in.readInt();
    }
}

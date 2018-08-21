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

package edu.umich.flowfence.study.presencebasedcontrol;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import edu.umich.flowfence.common.IEventChannelAPI;
import edu.umich.flowfence.common.FlowfenceContext;
import edu.umich.flowfence.common.TaintSet;
import edu.umich.flowfence.common.TaintableSharedPreferencesEditor;

public class PresenceQM implements Parcelable
{
    private static final String STORE_NAME = "PresenceKVS";
    private static final String LOC_KEY = "location";
    private static final String TAG = "PresenceQM";

    private static final String TAINT_TAG = "edu.umich.flowfence.study.presencebasedcontrol/presenceTaint";
    private static final String CHANNEL_NAME = "edu.umich.flowfence.study.presencebasedcontrol/presenceUpdateChannel";

    public PresenceQM()
    {
    }

    public static void putLoc(String val)
    {
        //get a KV store that is world readable
        SharedPreferences myprefs = FlowfenceContext.getInstance().getSharedPreferences(STORE_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor edit = myprefs.edit();

        edit.putString(LOC_KEY, val);

        //set a presence taint tag on this data
        TaintSet.Builder ts = new TaintSet.Builder();
        TaintSet builtTS;
        ts.addTaint(TAINT_TAG);
        builtTS = ts.build();
        ((TaintableSharedPreferencesEditor) edit).addTaint(LOC_KEY, builtTS);

        edit.commit();

        //fire an event to any listening QMs
        IEventChannelAPI eventApi = (IEventChannelAPI) FlowfenceContext.getInstance().getTrustedAPI("event");
        eventApi.fireEvent(builtTS, ComponentName.unflattenFromString(CHANNEL_NAME));

        Log.i(TAG, "updated KV with taint to value: " + val + ", and fired channel event");
    }

    //boiler-plate parcel serialize/deserialize
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {
    }

    public static final Parcelable.Creator<PresenceQM> CREATOR = new Parcelable.Creator<PresenceQM>()
    {
        public PresenceQM createFromParcel(Parcel in) {
            return new PresenceQM(in);
        }

        public PresenceQM[] newArray(int size) {
            return new PresenceQM[size];
        }
    };

    private PresenceQM(Parcel in)
    {
    }
}

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
package edu.umich.oasis.study.smartdevresponder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.smartthings.ISmartSwitchAPI;
import edu.umich.oasis.common.smartthings.SmartDevice;

/**
 * Created by earlence on 1/18/16.
 */
public class ResponderSoda implements Parcelable
{
    private static final String STORE_NAME = "PresenceKVS";
    private static final String LOC_KEY = "location";
    private static final String TAG = "ResponderSoda";
    private static final String SRC_PKG = "edu.umich.oasis.study.presencebasedcontrol";

    private static final String TIMING_TAG_END = "FencedSDR.TimeEnd";
    private static final boolean DEBUG_TIME = true;

    //state
    static String history = "";

    public ResponderSoda()
    {
        //history = "";
    }

    public static void pollPresenceAndCompute()
    {
        try {

            //the package context is important coz it makes us point towards the publisher

            SharedPreferences presencePrefs = OASISContext.getInstance().createPackageContext(SRC_PKG, 0).getSharedPreferences(STORE_NAME, Context.MODE_WORLD_READABLE);
            String presence = presencePrefs.getString(LOC_KEY, "null");

            Log.i(TAG, presence);
            history = getHistory();

            if(!history.equals(presence)) {

                String op = null;

                if (presence.equals("home")) {
                    Log.i(TAG, "let there be light!");
                    op = "on";
                } else if (presence.equals("away")) {
                    Log.i(TAG, "lights off!");
                    op = "off";
                }

                if (op != null) {
                    ISmartSwitchAPI switchAPI = (ISmartSwitchAPI) OASISContext.getInstance().getTrustedAPI("smartswitch");
                    List<SmartDevice> switches = switchAPI.getSwitches();

                    if(switches != null) {
                        for (SmartDevice ssw : switches) {
                            switchAPI.switchOp(op, ssw.getId());
                        }
                    } else {
                        Log.e(TAG, "no switches available");
                    }
                }

                history = presence;
                putHistory(history);

                if(DEBUG_TIME)
                {
                    Log.i(TIMING_TAG_END, "" + SystemClock.uptimeMillis());
                }
            }
        } catch(Exception e)
        {
            Log.e(TAG, "error: " + e);
        }
    }

    //use a private KV as a workaround for automatic state
    public static void putHistory(String hist)
    {
        //get a KV store that is world readable
        SharedPreferences myprefs = OASISContext.getInstance().getSharedPreferences("hist_store", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = myprefs.edit();

        edit.putString("history", hist);

        edit.commit();
    }

    public static String getHistory()
    {
        SharedPreferences myprefs = OASISContext.getInstance().getSharedPreferences("hist_store", Context.MODE_WORLD_READABLE);
        return myprefs.getString("history", "");
    }

    //boiler-plate parcel serialize/deserialize
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {
        //parcel.writeString(history);
    }

    public static final Parcelable.Creator<ResponderSoda> CREATOR = new Parcelable.Creator<ResponderSoda>()
    {
        public ResponderSoda createFromParcel(Parcel in) {
            return new ResponderSoda(in);
        }

        public ResponderSoda[] newArray(int size) {
            return new ResponderSoda[size];
        }
    };

    private ResponderSoda(Parcel in)
    {
        //history = in.readString();
    }
}

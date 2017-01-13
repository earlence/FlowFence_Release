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
 
package edu.umich.oasis.study.smartdevresponderexemplar;


import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;


final public class SmartThingsService
{
    private static final String TAG = "SmartThingsService";

    public static final String SINK_SMARTSWITCH = "SmartThings.SmartSwitch";


    //short-circuiting the OAuthToken handling process.
    private String OAuthToken = "cd584633-d888-47e2-9bab-584706f182cb";

    private ArrayList<SmartSwitch> switches; //a list of device-ids of all switches the user authorized during OAuth flow.

    private OkHttpClient httpClient;

    private static SmartThingsService mSelf = null;

    private final String endpointsURL = "https://graph.api.smartthings.com/api/smartapps/endpoints";
    private final String baseURL = "https://graph.api.smartthings.com";
    private String installationURL;
    private String fullBaseURL;

    private SmartThingsService()
    {
        httpClient = new OkHttpClient();
        switches = new ArrayList<SmartSwitch>();
        installationURL = "";
        fullBaseURL = "";

        Log.i(TAG, "create SmartThingsService");

        //contact SmartThings and pull all devices we can access
        pullDevices();
    }

    public static SmartThingsService getInstance()
    {
        if(mSelf == null) {
            mSelf = new SmartThingsService();
        }

        return mSelf;
    }

    private void pullDevices()
    {
        new Thread() {
            public void run() {

                String eURL = endpointsURL + "?access_token=" + OAuthToken;
                Request getEndpoints = new Request.Builder().url(eURL).build();

                try {
                    Response resp = httpClient.newCall(getEndpoints).execute();

                    if (resp.isSuccessful()) {
                        String jsonStr = resp.body().string();
                        JSONArray jobj = new JSONArray(jsonStr);

                        installationURL = jobj.getJSONObject(0).getString("url");
                        fullBaseURL = baseURL + installationURL;

                        Log.i(TAG, fullBaseURL);
                    }

                    //now get all devices
                    String getSwitchesURL = fullBaseURL + "/switches?access_token=" + OAuthToken;
                    Log.i(TAG, "making request: " + getSwitchesURL);
                    Request getSwitches = new Request.Builder().url(getSwitchesURL).build();
                    Response switchesResp = httpClient.newCall(getSwitches).execute();

                    if(switchesResp.isSuccessful())
                    {
                        String jsonResp = switchesResp.body().string();
                        JSONObject switchObj = new JSONObject(jsonResp);

                        Iterator<String> iter = switchObj.keys();
                        while(iter.hasNext())
                        {
                            String switchName = iter.next();
                            String switchId = switchObj.getString(switchName);
                            SmartSwitch aSwitch = new SmartSwitch(switchName, switchId);
                            switches.add(aSwitch);
                        }
                    }

                } catch (IOException ioe) {
                    Log.e(TAG, "error: " + ioe);
                } catch (JSONException jsone) {
                    Log.e(TAG, "error: " + jsone);
                }
            }
        }.start();
    }

    public ArrayList<SmartSwitch> getSwitches()
    {
        return switches;
    }

    public void switchOnOff(String op, String switchId)
    {
        SmartSwitch ss = null;
        for(SmartSwitch sw : switches)
        {
            if(sw.getSwitchId().equals(switchId))
            {
                ss = sw;
                break;
            }
        }

        final String onffURL = fullBaseURL + "/switchcontrol/" + switchId + "/" + op + "?access_token=" + OAuthToken;
        Log.i(TAG, "switchOnOff: " + onffURL);

        new Thread() {
            public void run()
            {
                try {
                    Request onOff = new Request.Builder().url(onffURL).build();
                    Response onOffResp = httpClient.newCall(onOff).execute();

                } catch(Exception e)
                {
                    Log.e(TAG, "error: " + e);
                }
            }
        }.start();
    }

    public String getSwitchState(String switchId)
    {
        return "";
    }
}

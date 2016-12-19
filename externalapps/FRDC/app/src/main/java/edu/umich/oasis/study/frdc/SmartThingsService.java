package edu.umich.oasis.study.frdc;

/**
 * Created by earlence on 1/20/16.
 */

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


/**
 * Created by earlence on 1/19/16.
 */
final public class SmartThingsService
{
    private static final String TAG = "SmartThingsService";

    public static final String SINK_SMARTSWITCH = "SmartThings.SmartSwitch";


    //short-circuiting the OAuthToken handling process.
    private String OAuthToken = "cd584633-d888-47e2-9bab-584706f182cb";

    private ArrayList<SmartDevice> devices; //a list of device-ids of all switches the user authorized during OAuth flow.

    private OkHttpClient httpClient;

    private static SmartThingsService mSelf = null;

    private final String endpointsURL = "https://graph.api.smartthings.com/api/smartapps/endpoints";
    private final String baseURL = "https://graph.api.smartthings.com";
    private String installationURL;
    private String fullBaseURL;

    private final String DEVICE_TYPE_URI_SWITCH = "switches";
    private final String DEVICE_TYPE_URI_LOCKS = "locks";
    private final String DEVICE_TYPE_CTRL_SWITCH = "switchcontrol";
    private final String DEVICE_TYPE_CTRL_LOCK = "lockcontrol";

    private SmartThingsService()
    {
        httpClient = new OkHttpClient();
        devices = new ArrayList<SmartDevice>();
        installationURL = "";
        fullBaseURL = "";

        Log.i(TAG, "create SmartThingsService");

        //contact SmartThings and pull all devices we can access
        pullDevices(DEVICE_TYPE_URI_SWITCH);
        pullDevices(DEVICE_TYPE_URI_LOCKS);
    }

    public static SmartThingsService getInstance()
    {
        if(mSelf == null) {
            mSelf = new SmartThingsService();
        }

        return mSelf;
    }

    private void pullDevices(final String deviceTypeStr)
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
                    String getSwitchesURL = fullBaseURL + "/" + deviceTypeStr + "?access_token=" + OAuthToken;
                    Log.i(TAG, "making request: " + getSwitchesURL);
                    Request getSwitches = new Request.Builder().url(getSwitchesURL).build();
                    Response switchesResp = httpClient.newCall(getSwitches).execute();

                    if(switchesResp.isSuccessful())
                    {
                        String jsonResp = switchesResp.body().string();
                        JSONObject switchObj = new JSONObject(jsonResp);

                        int dType = -1;
                        if(deviceTypeStr.equals(DEVICE_TYPE_URI_SWITCH))
                            dType = SmartDevice.TYPE_SWITCH;
                        else if(deviceTypeStr.equals(DEVICE_TYPE_URI_LOCKS))
                            dType = SmartDevice.TYPE_LOCK;

                        Iterator<String> iter = switchObj.keys();
                        while(iter.hasNext())
                        {
                            String switchName = iter.next();
                            String switchId = switchObj.getString(switchName);
                            SmartDevice aSwitch = new SmartDevice(switchName, switchId, dType);
                            devices.add(aSwitch);
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

    public ArrayList<SmartDevice> getSwitches()
    {
        ArrayList<SmartDevice> switches = new ArrayList<SmartDevice>();

        for(SmartDevice dev : devices)
        {
            if(dev.getType() == SmartDevice.TYPE_SWITCH)
                switches.add(dev);
        }

        return switches;
    }

    public ArrayList<SmartDevice> getLocks()
    {
        ArrayList<SmartDevice> locks = new ArrayList<SmartDevice>();

        for(SmartDevice dev : devices)
        {
            if(dev.getType() == SmartDevice.TYPE_LOCK)
                locks.add(dev);
        }

        return locks;
    }

    public void switchOnOff(String op, String switchId)
    {

        devOp(op, switchId, DEVICE_TYPE_CTRL_SWITCH);
    }

    public void lockUnlock(String op, String lockId)
    {
        devOp(op, lockId, DEVICE_TYPE_CTRL_LOCK);
    }

    private void devOp(String op, String id, String controlURIPart)
    {
        SmartDevice ss = null;

        for(SmartDevice sw : devices)
        {
            if(sw.getId().equals(id))
            {
                ss = sw;
                break;
            }
        }

        final String controlURL = fullBaseURL + "/" + controlURIPart + "/" + id + "/" + op + "?access_token=" + OAuthToken;
        Log.i(TAG, "switchOnOff: " + controlURL);

        new Thread() {
            public void run()
            {
                try {
                    Request ctrl = new Request.Builder().url(controlURL).build();
                    Response ctrlResp = httpClient.newCall(ctrl).execute();

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

package edu.umich.oasis.study.presencebasedcontrol;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import edu.umich.oasis.common.IEventChannelAPI;
import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.common.TaintableSharedPreferencesEditor;

/**
 * Created by earlence on 1/18/16.
 */
public class PresenceSoda implements Parcelable
{
    private static final String STORE_NAME = "PresenceKVS";
    private static final String LOC_KEY = "location";
    private static final String TAG = "PresenceSoda";

    private static final String TAINT_TAG = "edu.umich.oasis.study.presencebasedcontrol/presenceTaint";
    private static final String CHANNEL_NAME = "edu.umich.oasis.study.presencebasedcontrol/presenceUpdateChannel";

    public PresenceSoda()
    {
    }

    public static void putLoc(String val)
    {
        //get a KV store that is world readable
        SharedPreferences myprefs = OASISContext.getInstance().getSharedPreferences(STORE_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor edit = myprefs.edit();

        edit.putString(LOC_KEY, val);

        //set a presence taint tag on this data
        TaintSet.Builder ts = new TaintSet.Builder();
        TaintSet builtTS;
        ts.addTaint(TAINT_TAG);
        builtTS = ts.build();
        ((TaintableSharedPreferencesEditor) edit).addTaint(LOC_KEY, builtTS);

        edit.commit();

        //fire an event to any listening SODAs
        IEventChannelAPI eventApi = (IEventChannelAPI) OASISContext.getInstance().getTrustedAPI("event");
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

    public static final Parcelable.Creator<PresenceSoda> CREATOR = new Parcelable.Creator<PresenceSoda>()
    {
        public PresenceSoda createFromParcel(Parcel in) {
            return new PresenceSoda(in);
        }

        public PresenceSoda[] newArray(int size) {
            return new PresenceSoda[size];
        }
    };

    private PresenceSoda(Parcel in)
    {
    }
}

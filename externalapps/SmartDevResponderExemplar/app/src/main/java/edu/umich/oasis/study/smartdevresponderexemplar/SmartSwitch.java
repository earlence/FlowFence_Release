package edu.umich.oasis.study.smartdevresponderexemplar;

/**
 * Created by earlence on 1/20/16.
 */

import android.os.Parcel;
import android.os.Parcelable;

public class SmartSwitch implements Parcelable
{
    private String switchName;
    private String switchId;

    public SmartSwitch()
    {
        switchId = "";
        switchName = "";
    }

    public SmartSwitch(String _name, String _id)
    {
        switchId = _id;
        switchName = _name;
    }

    public String toString()
    {
        return switchName + "," + switchId;
    }

    public String getSwitchId()
    {
        return switchId;
    }

    public String getSwitchName()
    {
        return switchName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {
        parcel.writeString(switchName);
        parcel.writeString(switchId);
    }

    public static final Parcelable.Creator<SmartSwitch> CREATOR = new Parcelable.Creator<SmartSwitch>() {
        @Override
        public SmartSwitch createFromParcel(Parcel source) {
            return new SmartSwitch(source);
        }

        @Override
        public SmartSwitch[] newArray(int size) {
            return new SmartSwitch[size];
        }
    };

    private SmartSwitch(Parcel src)
    {
        switchName = src.readString();
        switchId = src.readString();
    }
}


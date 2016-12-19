package edu.umich.oasis.study.frdc;

/**
 * Created by earlence on 1/20/16.
 */

import android.os.Parcel;
import android.os.Parcelable;

public class SmartDevice implements Parcelable
{
    private String devName;
    private String devId;
    private int type;

    public static final int TYPE_SWITCH = 1;
    public static final int TYPE_LOCK = 2;

    public SmartDevice()
    {
        devId = "";
        devName = "";
        type = -1;
    }

    public SmartDevice(String _name, String _id, int _type)
    {
        devId = _id;
        devName = _name;
        type = _type;
    }

    public String toString()
    {
        return devName + "," + devId;
    }

    public String getId()
    {
        return devId;
    }

    public String getName()
    {
        return devName;
    }

    public int getType() { return type; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {
        parcel.writeString(devName);
        parcel.writeString(devId);
        parcel.writeInt(type);
    }

    public static final Parcelable.Creator<SmartDevice> CREATOR = new Parcelable.Creator<SmartDevice>() {
        @Override
        public SmartDevice createFromParcel(Parcel source) {
            return new SmartDevice(source);
        }

        @Override
        public SmartDevice[] newArray(int size) {
            return new SmartDevice[size];
        }
    };

    private SmartDevice(Parcel src)
    {
        devName = src.readString();
        devId = src.readString();
        type = src.readInt();
    }
}


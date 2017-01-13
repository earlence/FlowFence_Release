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
 
package edu.umich.oasis.study.frdc;


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


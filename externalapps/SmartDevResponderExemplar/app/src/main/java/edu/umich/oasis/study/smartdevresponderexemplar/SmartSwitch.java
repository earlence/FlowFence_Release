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


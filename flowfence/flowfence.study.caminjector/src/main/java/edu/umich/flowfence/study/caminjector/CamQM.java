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

package edu.umich.flowfence.study.caminjector;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import edu.umich.flowfence.common.IEventChannelAPI;
import edu.umich.flowfence.common.FlowfenceContext;
import edu.umich.flowfence.common.TaintSet;

public class CamQM implements Parcelable
{
    private static final String TAG = "CamQM";

    private static final String TAINT_TAG = "edu.umich.flowfence.study.caminjector/cameraBMPTaint";
    private static final String CHANNEL_NAME = "edu.umich.flowfence.study.caminjector/cameraBMPChannel";

    public static final int OPCODE_ENROLL = 1;
    public static final int OPCODE_ENROLLTEST = 2;
    public static final int OPCODE_ENROLL_REF = 3;
    public static final int OPCODE_ENROLL_NONREF = 4;
    public static final int OPCODE_RECOG = 5;

    public CamQM()
    {
    }

    public static void sendBMP(Bitmap bmp, int index, int opcode, long deliveryTime)
    {
        //set a camera taint tag on this data
        TaintSet.Builder ts = new TaintSet.Builder();
        TaintSet builtTS;
        ts.addTaint(TAINT_TAG);
        builtTS = ts.build();

        if(bmp != null) {
            //fire an event to any listening QMs
            IEventChannelAPI eventApi = (IEventChannelAPI) FlowfenceContext.getInstance().getTrustedAPI("event");
            eventApi.fireEvent(builtTS, ComponentName.unflattenFromString(CHANNEL_NAME), opcode, index, bmp, deliveryTime);
        }
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

    public static final Parcelable.Creator<CamQM> CREATOR = new Parcelable.Creator<CamQM>()
    {
        public CamQM createFromParcel(Parcel in) {
            return new CamQM(in);
        }

        public CamQM[] newArray(int size) {
            return new CamQM[size];
        }
    };

    private CamQM(Parcel in)
    {
    }
}

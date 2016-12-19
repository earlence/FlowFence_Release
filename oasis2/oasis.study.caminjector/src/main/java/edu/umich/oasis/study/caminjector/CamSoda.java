package edu.umich.oasis.study.caminjector;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import edu.umich.oasis.common.IEventChannelAPI;
import edu.umich.oasis.common.OASISContext;
import edu.umich.oasis.common.TaintSet;

/**
 * Created by earlence on 2/8/16.
 */
public class CamSoda implements Parcelable
{
    private static final String TAG = "CamSoda";

    private static final String TAINT_TAG = "edu.umich.oasis.study.caminjector/cameraBMPTaint";
    private static final String CHANNEL_NAME = "edu.umich.oasis.study.caminjector/cameraBMPChannel";

    public static final int OPCODE_ENROLL = 1;
    public static final int OPCODE_ENROLLTEST = 2;
    public static final int OPCODE_ENROLL_REF = 3;
    public static final int OPCODE_ENROLL_NONREF = 4;
    public static final int OPCODE_RECOG = 5;

    public CamSoda()
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
            //fire an event to any listening SODAs
            IEventChannelAPI eventApi = (IEventChannelAPI) OASISContext.getInstance().getTrustedAPI("event");
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

    public static final Parcelable.Creator<CamSoda> CREATOR = new Parcelable.Creator<CamSoda>()
    {
        public CamSoda createFromParcel(Parcel in) {
            return new CamSoda(in);
        }

        public CamSoda[] newArray(int size) {
            return new CamSoda[size];
        }
    };

    private CamSoda(Parcel in)
    {
    }
}

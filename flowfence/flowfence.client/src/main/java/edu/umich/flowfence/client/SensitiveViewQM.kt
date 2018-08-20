package edu.umich.flowfence.client

import android.os.Parcel
import android.os.Parcelable
import edu.umich.flowfence.common.ISensitiveViewAPI
import edu.umich.flowfence.common.OASISContext

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/17/2018 21:42
 */
 
class SensitiveViewQM() : Parcelable {

    fun setValue(viewId: String, value: String){
        val api: ISensitiveViewAPI = OASISContext.getInstance().getTrustedAPI("ui") as ISensitiveViewAPI
        api.addSensitiveValue(viewId, value)
    }

    // Boilerplate parcel
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel?, i: Int) {}
    constructor(parcel: Parcel?) : this()
    companion object {
        val CREATOR: Parcelable.Creator<SensitiveViewQM> = object: Parcelable.Creator<SensitiveViewQM> {
            override fun createFromParcel(p0: Parcel?): SensitiveViewQM {
                return SensitiveViewQM(p0)
            }

            override fun newArray(p0: Int): Array<SensitiveViewQM> {
                return arrayOf()
            }
        }
    }
}
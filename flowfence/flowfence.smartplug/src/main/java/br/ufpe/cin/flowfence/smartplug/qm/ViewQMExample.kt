package br.ufpe.cin.flowfence.smartplug.qm

import android.os.Parcel
import android.os.Parcelable
import android.widget.Toast
import edu.umich.flowfence.common.*

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/17/2018 16:42
 */

open class ViewQMExample : Parcelable {

    constructor()

    val TAG = ViewQMExample::class.java.simpleName
    val TAINT: String = "br.ufpe.cin.flowfence.smartplug/UI"
    val TAINT_SET = TaintSet.Builder().addTaint(TAINT).build()


    fun toastValue(viewId: Int){
        val value = getValue(viewId)
        showToast("Value = $value")
    }

    fun getValue(viewId: Int) : String {
        val api: ISensitiveViewAPI = OASISContext.getInstance().getTrustedAPI("ui") as ISensitiveViewAPI
        val value = api.readSensitiveValue(viewId.toString(), TAINT_SET)
        return value
    }

    fun showToast(message: String){
        val toastMethod: IDynamicAPI = OASISContext.getInstance().getTrustedAPI("toast") as IDynamicAPI
        toastMethod.invoke("showText", message, Toast.LENGTH_LONG)
    }


    override fun writeToParcel(parcel: Parcel, flags: Int) {}

    override fun describeContents(): Int {
        return 0
    }

    constructor(parcel: Parcel)
    companion object CREATOR : Parcelable.Creator<ViewQMExample> {
        override fun createFromParcel(parcel: Parcel): ViewQMExample {
            return ViewQMExample(parcel)
        }

        override fun newArray(size: Int): Array<ViewQMExample?> {
            return arrayOfNulls(size)
        }
    }

}
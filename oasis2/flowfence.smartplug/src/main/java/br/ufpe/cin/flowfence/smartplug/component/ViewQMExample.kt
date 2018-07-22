package br.ufpe.cin.flowfence.smartplug.component

import android.os.Parcel
import android.os.Parcelable
import android.widget.Toast
import edu.umich.oasis.common.IDynamicAPI
import edu.umich.oasis.common.ISensitiveViewAPI
import edu.umich.oasis.common.OASISContext
import edu.umich.oasis.common.TaintSet

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
        val toastMethod: IDynamicAPI = OASISContext.getInstance().getTrustedAPI("toast") as IDynamicAPI
        toastMethod.invoke("showText", "Value tainted = ${value}", Toast.LENGTH_LONG)
    }

    fun getValue(viewId: Int) : String {
        val api: ISensitiveViewAPI = OASISContext.getInstance().getTrustedAPI("ui") as ISensitiveViewAPI
        val value = api.readSensitiveValue(viewId.toString(), TAINT_SET)
        val x: String = if(value == null) { ""} else {value}
        return x
    }

    // Boilerplate parcel
    constructor(parcel: Parcel?)
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel?, i: Int) {}
    companion object {
        val CREATOR: Parcelable.Creator<ViewQMExample> = object: Parcelable.Creator<ViewQMExample> {
            override fun createFromParcel(p0: Parcel?): ViewQMExample {
                return ViewQMExample(p0)
            }

            override fun newArray(p0: Int): Array<ViewQMExample> {
                return arrayOf()
            }
        }
    }
}
package br.ufpe.cin.flowfence.smartplug.qm

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import edu.umich.oasis.common.*
import org.json.JSONObject

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/17/2018 16:42
 */

open class TestQMExample : Parcelable {

    constructor()
    val TAG = TestQMExample::class.java.simpleName

    val TAINT: String = "br.ufpe.cin.flowfence.smartplug/UI"
    val TAINT_SET = TaintSet.Builder().addTaint(TAINT).build()

    constructor(parcel: Parcel) : this() {
    }

    fun toastValue(viewId: Int){
        val value = getValue(viewId)
        showToast(value)
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

    companion object CREATOR : Parcelable.Creator<TestQMExample> {
        override fun createFromParcel(parcel: Parcel): TestQMExample {
            return TestQMExample(parcel)
        }

        override fun newArray(size: Int): Array<TestQMExample?> {
            return arrayOfNulls(size)
        }
    }

}
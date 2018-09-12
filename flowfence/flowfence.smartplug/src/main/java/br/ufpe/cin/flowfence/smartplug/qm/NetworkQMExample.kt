package br.ufpe.cin.flowfence.smartplug.qm

import android.os.Parcel
import android.os.Parcelable
import edu.umich.flowfence.common.*

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/25/2018 13:36
 */
 
class NetworkQMExample : Parcelable {

    constructor()

    val TAG = NetworkQMExample::class.java.simpleName

    var response: String = ""

    fun getState(url: String) : String {
        val api: INetworkAPI = FlowfenceContext.getInstance().getTrustedAPI("network") as INetworkAPI
        return api.get(url)
    }

    fun changeState(url: String, state: String): String {
        val api: INetworkAPI = FlowfenceContext.getInstance().getTrustedAPI("network") as INetworkAPI
        var map = HashMap<String, String>()
        map["state"] = state
        return api.post(url, map)
    }


    // Boilerplate parcel
    constructor(parcel: Parcel?)
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel?, i: Int) {}
    companion object {
        val CREATOR: Parcelable.Creator<NetworkQMExample> = object: Parcelable.Creator<NetworkQMExample> {
            override fun createFromParcel(p0: Parcel?): NetworkQMExample {
                return NetworkQMExample(p0)
            }

            override fun newArray(p0: Int): Array<NetworkQMExample> {
                return arrayOf()
            }
        }
    }
}
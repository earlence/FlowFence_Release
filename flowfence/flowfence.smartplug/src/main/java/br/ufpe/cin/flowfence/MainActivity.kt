package br.ufpe.cin.flowfence

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat.requestPermissions
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.AppCompatButton
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import br.ufpe.cin.flowfence.smartplug.R
import br.ufpe.cin.flowfence.smartplug.extension.showToast
import br.ufpe.cin.flowfence.smartplug.qm.NetworkQMExample
import br.ufpe.cin.flowfence.smartplug.qm.ViewQMExample
import br.ufpe.cin.smartplug.network.WifiConnectionManager
import edu.umich.flowfence.client.SensitiveEditText
import edu.umich.flowfence.client.FlowfenceConnection
import edu.umich.flowfence.client.Sealed
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    val TAG = MainActivity::class.java.simpleName

    val ACCESS_FINE_LOCATION_PERMISSION_CODE = 12
    var alert: android.support.v7.app.AlertDialog? = null

    lateinit var progressDialog: ProgressDialog
    var connection: FlowfenceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressDialog = ProgressDialog(this)
        initiateFlowFence()
        pairing_no_qm_button.setOnClickListener {
            pairWithDevice(false)
        }

        pairing_qm_button.setOnClickListener {
            pairWithDevice(true)
        }

        toast_qm_button.setOnClickListener{
            toastTextWithFlowfence()
        }

        get_state.setOnClickListener {
            getPlugState()
        }

        turn_on_off.setOnClickListener {
            val turnOn = (it as AppCompatButton).text == "Turn on"
            turn_on_off.text = "Turn ${if(turnOn) "off" else "on"}"
            setPlugState(turnOn)
        }
    }

    fun toastTextWithFlowfence(){
        val constructor = connection!!.resolveConstructor(ViewQMExample::class.java)
        val qm: Sealed<ViewQMExample> = constructor.call()

        val toastValue = connection!!.resolveInstance(Void::class.java, ViewQMExample::class.java, "toastValue", Int::class.java)
        qm.buildCall(toastValue).arg(sensitiveEditText.id).call()

        // Cannot access opaque-handle value
        //val getValue = connection!!.resolveInstance(Void.TYPE, ViewQMExample::class.java, "getValue", String::class.java)
        //val result: Sealed<String> = qm.buildCall(getValue).arg(sensitiveEditText.id).call()
        //result.declassify()

        // Cannot access set sensitive text value directly
        //sensitiveEditText.setText("TESTE")

        // Right way to set value programmtically
        //val setValue = connection!!.resolveInstance(Void.TYPE, ViewQMExample::class.java, "setValue", String::class.java)
        //qm.buildCall(setValue).arg("TESTE").call()
    }

    fun getPlugState() {
        val qmNetwork: Sealed<NetworkQMExample> = connection!!.resolveConstructor(NetworkQMExample::class.java).call()
        val getState = connection!!.resolveInstance(String::class.java, NetworkQMExample::class.java, "getState", String::class.java)
        val response: Sealed<String> = qmNetwork.buildCall(getState).arg("https://flowfence-testserver-211220.appspot.com").call()

        val qmView: Sealed<ViewQMExample> = connection!!.resolveConstructor(ViewQMExample::class.java).call()
        val showToast = connection!!.resolveInstance(Void::class.java, ViewQMExample::class.java, "showToast", String::class.java)
        qmView.buildCall(showToast).arg(response).call()
    }

    fun setPlugState(turnOn: Boolean){
        val qmNetwork: Sealed<NetworkQMExample> = connection!!.resolveConstructor(NetworkQMExample::class.java).call()
        val changeState = connection!!.resolveInstance(String::class.java, NetworkQMExample::class.java, "changeState", String::class.java, String::class.java)
        val state = if(turnOn) "on" else "off"
        qmNetwork.buildCall(changeState).arg("https://flowfence-testserver-211220.appspot.com").arg(state).call()
    }

    private fun initiateFlowFence(){
        Log.i(TAG, "Binding to FlowFence...")
        FlowfenceConnection.bind(this, object: FlowfenceConnection.Callback {
            override fun onConnect(conn: FlowfenceConnection?) {
                Log.i(TAG, "Connected to FlowFence")
                connection = conn
                sensitiveEditText.connection = conn
            }
        })
    }


    // Simulates the pairing process with a real device
    fun pairWithDevice(useFlowfence: Boolean){
        if(!hasPermissions()){
            requestAndroidPermissions()
        }

        // 1. Look for device on the network (UDP broadcast)
        // 2. Found device, now force Android device to enter its created hotspot (Wifi)
        // 3. Now that the Android device is on the hotspot, prompt the user to select its prefered home WiFi and type its password
        Observable.timer(3, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe{ showProgressBar("Looking for the smart plug on the network...") }
                .subscribe {
                    hideProgressBar()
                    showAlert("Found device and connected to its created WIFi hotspot.")
                    discoverWifi(useFlowfence)
                }
    }

    private fun discoverWifi(useFlowfence: Boolean){
        hideAlert()

        val wifiConnManager = WifiConnectionManager(this.applicationContext)
        registerReceiver(wifiConnManager.wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        wifiConnManager.getWifiConnections()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe({ alert(Appcompat, "Now scanning for nearby WiFI")})
                .doAfterTerminate {
                    hideProgressBar()
                    unregisterReceiver(wifiConnManager.wifiReceiver)
                }
                .subscribe { detectedWifis ->
                    val wifiNames = detectedWifis.map{it.SSID}
                    selector("Please select the WiFi you wish the plug to connect to",
                            wifiNames,
                            { dialogInterface, selectedIndex -> askForPassword(wifiNames[selectedIndex], useFlowfence)})
                }
    }

    fun buildAlert(alert: AlertDialog.Builder, wifiName: String, passwordEditText: EditText){
        try {
            with(alert) {
                setTitle("Please type ${wifiName} password")
                passwordEditText!!.hint = "Password"
                passwordEditText!!.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPositiveButton("Ok") { dialog, button ->
                    continuePairing(passwordEditText!!.text.toString())
                }
            }

            val dialog = alert.create()
            dialog.setView(passwordEditText)
            dialog.show()
        }
        catch(e: Exception){

        }
    }

    fun askForPassword(wifiName: String, useFlowfence: Boolean){
        val alert = AlertDialog.Builder(this)
        if(useFlowfence){
            buildAlert(alert, wifiName, SensitiveEditText(this@MainActivity))
        }
        else{
            buildAlert(alert, wifiName, EditText(this@MainActivity))
        }
    }

    fun continuePairing(password: String){
        // This is actually present in best-selling device's APP (e.g., TP-Link Kasa)
        Log.i(TAG, "Logging typed password: $password")
        showToast("Typed password = $password")
        // Here the app connects to the WIFI and continue pairing process.
    }

    // Boilerplate UI
    fun showAlert(message: String){
        alert = alert(Appcompat, message).show()
    }

    fun hideAlert(){
        alert?.cancel()
    }

    fun showProgressBar(message: String){
        progressDialog.setCancelable(false)
        progressDialog.setMessage(message)
        progressDialog.show()
    }

    fun hideProgressBar(){
        progressDialog.cancel()
    }


    // Boilerplate Android permission
    fun requestAndroidPermissions(){
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.forEach {
            if(ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(this, permissions, ACCESS_FINE_LOCATION_PERMISSION_CODE)
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            ACCESS_FINE_LOCATION_PERMISSION_CODE -> {
                if(grantResults.isEmpty() || grantResults.any {t -> t != PackageManager.PERMISSION_GRANTED }){
                    Toast.makeText(this, "Cannot run unless given appropriate permission(s)", Toast.LENGTH_LONG)
                }
            }
        }
    }


    fun hasPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.all { t -> ContextCompat.checkSelfPermission(this@MainActivity, t) == PackageManager.PERMISSION_GRANTED }
    }

    companion object {
        fun getContext() : Context{
            return this.getContext()
        }
    }
}
package br.ufpe.cin.smartplug

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.content.ContextCompat
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import br.ufpe.cin.smartplug.network.WifiConnectionManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.jetbrains.anko.design.snackbar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    val TAG = MainActivity::class.java.simpleName
    val ACCESS_FINE_LOCATION_PERMISSION_CODE = 12
    lateinit var progressDialog: ProgressDialog
    var alert: android.support.v7.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressDialog = ProgressDialog(this)
        pairing_button.setOnClickListener {pairWithDevice()}
    }

    // Simulates the pairing process with a real device
    fun pairWithDevice(){
        if(!hasPermissions()){
            Observable.timer(3, TimeUnit.SECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {requestPermissions()}
            return
        }



        // 1. Look for device on the network (UDP broadcast)
        // 2. Found device, now force Android device to enter its created hotspot (Wifi)
        // 3. Now that the Android device is on the hotspot, prompt the user to select its preffered home WiFi and type its password
        Observable.timer(3, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe({ showProgressBar("Looking for the smart plug on the network...") })
                .subscribe({
                    hideProgressBar()
                    showAlert("Found device and connected to its created WIFi hotspot.")
                    discoverWifi()
                })
    }

    private fun discoverWifi(){
        hideAlert()

        val wifiConnManager = WifiConnectionManager(this.applicationContext)
        registerReceiver(wifiConnManager.wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        wifiConnManager.getWifiConnections()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe({ alert(Appcompat, "Now scanning for nearby WiFI")})
                .doOnComplete { hideProgressBar()}
                .subscribe { detectedWifis ->
                    val wifiNames = detectedWifis.map{it.SSID}
                    selector("Please select the WiFi you wish the plug to connect to",
                            wifiNames,
                            { dialogInterface, selectedIndex -> askForPassword(wifiNames[selectedIndex])})
                }
    }

    fun askForPassword(wifiName: String){
        val alert = AlertDialog.Builder(this)
        var passwordEditText:EditText? = null
        with(alert){
            setTitle("Please type ${wifiName} password")
            passwordEditText = EditText(this@MainActivity)
            passwordEditText!!.hint = "Password"
            passwordEditText!!.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD


            setPositiveButton("Ok"){
                dialog, button -> continuePairing(passwordEditText!!.text.toString())
            }
        }

        val dialog = alert.create()
        dialog.setView(passwordEditText)
        dialog.show()
    }

    fun continuePairing(password: String){
        Log.i(TAG, "TYPED PASSWORD = ${password}")
        // Do some more stuff
    }

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


    // Boilerplate Android permission-request
    fun requestPermissions(){
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.forEach {
            if(ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(permissions, ACCESS_FINE_LOCATION_PERMISSION_CODE)
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
}



package br.ufpe.cin.flowfence

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.Toast
import br.ufpe.cin.flowfence.smartplug.R
import br.ufpe.cin.flowfence.smartplug.component.ViewQMExample
import edu.umich.oasis.client.SensitiveEditText
import edu.umich.oasis.client.OASISConnection
import edu.umich.oasis.client.Sealed
import edu.umich.oasis.client.Soda
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat

class MainActivity : AppCompatActivity() {

    val TAG = MainActivity::class.java.simpleName

    val ACCESS_FINE_LOCATION_PERMISSION_CODE = 12
    var alert: android.support.v7.app.AlertDialog? = null

    lateinit var progressDialog: ProgressDialog
    var connection: OASISConnection? = null

    var qm: Soda.S0<SensitiveEditText>? = null
    var readValue: Soda.S1<SensitiveEditText, String>? = null
    var handle: Sealed<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressDialog = ProgressDialog(this)
        initiateFlowFence()

        pairing_button.setOnClickListener {
                val constructor = connection!!.resolveConstructor(ViewQMExample::class.java)
                val qmExample: Sealed<ViewQMExample> = constructor.call()
                Log.i(TAG, "SensitiveET ID = ${sensitiveEditText.id.toString()}")
                val toastValue = connection!!.resolveInstance(Void::class.java, ViewQMExample::class.java, "toastValue", Int::class.java)


                val getValue = connection!!.resolveInstance(String::class.java, ViewQMExample::class.java, "getValue", Int::class.java)

                //val result: Sealed<String> = qm.buildCall(getValue).arg(sensitiveEditText.id).call()
                //result.declassify()

                qmExample.buildCall(toastValue).arg(sensitiveEditText.id).call()

                // Cannot do it
                //sensitiveEditText.setText("TESTE")


//            val getValue = connection!!.resolveInstance(String::class.java, ViewQMExample::class.java, "getValue", Int::class.java)


//            val toastValue = connection!!.resolveStatic(Void.TYPE, ViewQMExample::class.java, "toastValue", Int::class.java)
//            toastValue.arg(sensitiveEditText.id).call()


            //val text: Sealed<String> = qm.buildCall(getValue).arg(sensitiveEditText.id).call()



//            val setEditText = connection!!.resolveInstance(Void.TYPE, ViewQuarentineModule::class.java, "setSensitive",SensitiveEditText::class.java)
//            qm.buildCall(setEditText).arg(sensitiveEditText).call()

            Log.i(TAG, "Read value: ${sensitiveEditText.text}")

//            val qm: Sealed<ViewQuarentineModule> = constructor.arg(sensitiveEditText).call()
//            val toastValue = connection!!.resolveInstance(String::class.java, ViewQuarentineModule::class.java, "toastValue")
//            toastValue.arg(qm).call()




//
//            readValue = connection.resolveInstance(String::class.java, ViewQuarentineModuleBackup::class.java, "readValue")
//
            //val currentText = testasd.text
           // Log.i(TAG, currentText.toString())
        }


    }

    fun initiateFlowFence(){
        Log.i(TAG, "Binding to OASIS...");
        OASISConnection.bind(this, object: OASISConnection.Callback {
            override fun onConnect(conn: OASISConnection?) {
                Log.i(TAG, "Connected to OASIS")
                connection = conn
                sensitiveEditText.connection = conn
            }
        })
    }

    fun putValue(value: String){
        if(connection != null){
            try{
                Log.i(TAG, "Calling setValue method")
                //val method: Soda.S2<String, Boolean, Void> = connection!!.resolveStatic(Void.TYPE, KeyValueTest::class.java,"setValue",String::class.java,Boolean::class.java)
                Log.i(TAG, "Resolved QM with sucess!!")
//                method.arg(value)
//                        .arg(true)
//                        .call()
            }
            catch (e: Exception){
                Log.e(TAG, e.toString())
            }
        }
    }

    fun toastValue(){
//        val toastValueSoda = connection!!.resolveStatic(Void.TYPE, KeyValueTest::class.java, "toastValue")
        //toastValueSoda.call()
    }

//    fun readValue(){
//        if(connection != null){
//            try{
//                ctor = connection!!.resolveConstructor(TestSoda::class.java)
//                val soda1: Sealed<TestSoda> = ctor!!.call()
//                val readLoc: Soda.S1<TestSoda, Void> = connection!!.resolveInstance(Void::class.java, TestSoda::class.java, "readLoc")
//                readLoc.arg(soda1).call()
//            }catch (e: Exception){
//                Log.e(TAG, e.toString())
//            }
//        }
//    }
//
//
//    // Simulates the pairing process with a real device
//    fun pairWithDevice(){
//        if(!hasPermissions()){
//            Observable.timer(3, TimeUnit.SECONDS)
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe {requestPermissions()}
//            return
//        }
//
//
//        // 1. Look for device on the network (UDP broadcast)
//        // 2. Found device, now force Android device to enter its created hotspot (Wifi)
//        // 3. Now that the Android device is on the hotspot, prompt the user to select its preffered home WiFi and type its password
//        Observable.timer(3, TimeUnit.SECONDS)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .doOnSubscribe({ showProgressBar("Looking for the smart plug on the network...") })
//                .subscribe {
//                    hideProgressBar()
//                    showAlert("Found device and connected to its created WIFi hotspot.")
//                    discoverWifi()
//                }
//    }
//
//    private fun discoverWifi(){
//        hideAlert()
//
//        val wifiConnManager = WifiConnectionManager(this.applicationContext)
//        registerReceiver(wifiConnManager.wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
//
//        wifiConnManager.getWifiConnections()
//                .observeOn(AndroidSchedulers.mainThread())
//                .doOnSubscribe({ alert(Appcompat, "Now scanning for nearby WiFI")})
//                .doOnComplete { hideProgressBar()}
//                .subscribe { detectedWifis ->
//                    val wifiNames = detectedWifis.map{it.SSID}
//                    selector("Please select the WiFi you wish the plug to connect to",
//                            wifiNames,
//                            { dialogInterface, selectedIndex -> askForPassword(wifiNames[selectedIndex])})
//                }
//    }
//
//    fun askForPassword(wifiName: String){
//        val alert = AlertDialog.Builder(this)
//        var passwordEditText: SensitiveEditText? = null
//        with(alert){
//            setTitle("Please type ${wifiName} password")
//            passwordEditText = SensitiveEditText(this@MainActivity)
//            passwordEditText!!.hint = "Password"
//            passwordEditText!!.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
//
//            setPositiveButton("Ok"){
//                dialog, button -> continuePairing(passwordEditText!!.text.toString())
//            }
//        }
//
//        val dialog = alert.create()
//        dialog.setView(passwordEditText)
//        dialog.show()
//    }

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
                //requestPermissions(permissions, ACCESS_FINE_LOCATION_PERMISSION_CODE)
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

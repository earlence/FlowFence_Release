package br.ufpe.cin.smartplug.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.wifiManager
import java.util.concurrent.TimeUnit
import java.util.function.Consumer


/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/09/2018 11:27
 */

class WifiConnectionManager (context: Context) {

    val TAG = WifiConnectionManager::class.java.simpleName
    val wifiManager: WifiManager = context.wifiManager

    lateinit var scanResults: MutableList<ScanResult>

    val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent!!.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                scanResults = wifiManager.scanResults.distinct().toMutableList()
                Log.i(TAG, "Received ${scanResults.size} WiFi(s)")
            }
        }
    }

    fun getWifiConnections(): Observable<MutableList<ScanResult>> {
        wifiManager.startScan()
        return Observable.timer(3, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .concatMap { Observable.just(scanResults) }
    }
}
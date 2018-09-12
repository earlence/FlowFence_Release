package br.ufpe.cin.flowfence.smartplug.extension

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import org.jetbrains.anko.design.snackbar


/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/23/2018 13:35
 */
 
 fun Context.showToast(message: String, length: Int = Toast.LENGTH_LONG){
     Toast.makeText(this, message, length).show()
 }

fun Context.showSnackbar(message: String){
    val view: View = (this as Activity).window.decorView.rootView
    snackbar(view, message)
}
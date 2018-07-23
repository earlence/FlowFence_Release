package br.ufpe.cin.flowfence.smartplug.extension

import android.content.Context
import android.widget.Toast

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/23/2018 13:35
 */
 
 fun Context.showToast(message: String, length: Int = Toast.LENGTH_LONG){
     Toast.makeText(this, message, length).show()
 }
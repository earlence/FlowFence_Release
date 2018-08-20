package edu.umich.flowfence.client

import android.content.Context
import android.text.Editable
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.util.Log
import android.widget.EditText

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/14/2018 10:52
 */

class SensitiveEditText: EditText {

    var constructorDone = false // Need this as TextView constructor calls setText() directly
    constructor(context: Context) : super(context){ constructorDone = true }
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet){ constructorDone = true }
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle){ constructorDone = true }

    val TAG = SensitiveEditText::class.java.simpleName
    var connection: OASISConnection? = null

    override fun getText(): Editable {
        val stackTrace: Array<StackTraceElement> = Thread.currentThread().stackTrace
        return if(stackTrace.any { t -> t.className.contains("android.widget.TextView") }){
            // Call from Android SDK, so it is SAFE to return value without Flowfence
            val currentText = super.getText().toString()
            setValue(currentText)
            SpannableStringBuilder(currentText)
        }
        else{
            Log.i(TAG, "Cannot access value directly. Please do it via Quarentine Module/FlowFence Trusted API")
            SpannableStringBuilder("")
        }
    }


    override fun setText(text: CharSequence?, type: BufferType?) {
        if(!constructorDone) {
            // First call is allowed as it comes from android SDK before constructing this component
            super.setText(text, type)
        }
        else{
            Log.i(TAG, "Cannot set value directly. Please do it via Quarentine Module/FlowFence Trusted API with setValue() call")
        }
    }

    // Set value via Quarentine Module
    fun setValue(value: String){
        if(connection == null){
            Log.i(TAG, "Please bind the FlowFence connection to this component.")
        }
        else {
            val constructor = connection!!.resolveConstructor(SensitiveViewQM::class.java)
            val setValue = connection!!.resolveInstance(Void.TYPE, SensitiveViewQM::class.java, "setValue", String::class.java, String::class.java)
            constructor.call().buildCall(setValue).arg(id.toString()).arg(value).call()
        }
    }
}
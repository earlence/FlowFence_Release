package br.ufpe.cin.flowfencehelloworld;
/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 10/30/2018 15:01
 */

import android.os.Parcelable;
import android.widget.Toast;

import edu.umich.flowfence.common.FlowfenceContext;
import edu.umich.flowfence.common.IDynamicAPI;

public class HelloWorldQM {
    public static void toastHelloWorld(){
        IDynamicAPI toast = (IDynamicAPI) FlowfenceContext.getInstance().getTrustedAPI("toast");
        toast.invoke("showText", "Hello World!", Toast.LENGTH_LONG);
    }
}

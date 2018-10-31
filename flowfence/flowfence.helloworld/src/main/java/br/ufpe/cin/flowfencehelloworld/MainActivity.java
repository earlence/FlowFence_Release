package br.ufpe.cin.flowfencehelloworld;

import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import edu.umich.flowfence.client.FlowfenceConnection;
import edu.umich.flowfence.client.QuarentineModule;
import edu.umich.flowfence.client.Sealed;

public class MainActivity extends AppCompatActivity {

    private static String TAG = MainActivity.class.getSimpleName();
    private FlowfenceConnection connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connectToFlowfence();
        findViewById(R.id.click_me).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toastHelloWorld();
            }
        });
    }

    public void connectToFlowfence() {
        Log.i(TAG, "Binding to FlowFence...");
        FlowfenceConnection.bind(this, new FlowfenceConnection.DisconnectCallback() {
            @Override
            public void onConnect(FlowfenceConnection conn) throws Exception {
                 Log.i(TAG, "Bound to FlowFence");
                connection = conn;
            }

            @Override
            public void onDisconnect(FlowfenceConnection conn) throws Exception {
                Log.i(TAG, "Unbound from FlowFence");
                connection = null;
            }
        });
    }

    private void toastHelloWorld(){
        try {
            QuarentineModule.S0<Void> toastMethod = connection.resolveStatic(void.class, HelloWorldQM.class,"toastHelloWorld");
            toastMethod.call();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


}

package edu.umich.oasis.study.presencebasedcontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/*
This app receives location updates thru FireBase from a phone
"in the field", and pushes the latest location value to a KV store
for "Smart Home Apps" to access and control devices.
We don't want location updates to be leaked, so we
set an oasis policy that only allows the flow:
location -> smartthings
 */

public class MainActivity extends Activity {

    Button cmdConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cmdConnect = (Button) findViewById(R.id.cmdConnect);
        cmdConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent ss = new Intent(getApplicationContext(), PresenceInjector.class);
                startService(ss);
            }
        });
    }
}

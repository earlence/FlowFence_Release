package edu.umich.oasis.study.smartdevresponder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/*
This is a "Smart home app". It listens for presence values from the PresenceKV store
if presence == home then turn on lights
else if presence == away then turn off lights

For now, it uses timed soda invocation to keep checking for change in presence values
in the KV store
 */

public class SmartDevResponderActivity extends Activity {

    Button cmdConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_dev_responder);

        cmdConnect = (Button) findViewById(R.id.cmdConnect);
        cmdConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent ss = new Intent(getApplicationContext(), ResponderService.class);
                startService(ss);
            }
        });

    }
}

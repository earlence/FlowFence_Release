package edu.umich.oasis.study.fencedhr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class FencedHRMain extends Activity {

    Button cmdConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fenced_hrmain);

        cmdConnect = (Button) findViewById(R.id.cmdConnect);

        cmdConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent x = new Intent(getApplicationContext(), HRService.class);
                startService(x);
            }
        });
    }
}

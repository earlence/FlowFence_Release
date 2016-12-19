package edu.umich.oasis.study.caminjector;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class CamInjectorMain extends Activity {

    private static final String TAG = "CamInjectorMain";

    Button cmdConnect, cmdNewBmp, cmdEnrollTest, cmdRecogTest;

    CamInjector mService = null;
    boolean mBound = false;

    private static final boolean DEBUG_REF = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam_injector_main);

        cmdConnect = (Button) findViewById(R.id.cmdConnect);
        cmdNewBmp = (Button) findViewById(R.id.cmdNewBmp);
        cmdEnrollTest = (Button) findViewById(R.id.cmdEnrollTest);
        cmdRecogTest = (Button) findViewById(R.id.cmdRecogTest);

        cmdConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(getApplicationContext(), CamInjector.class);
                bindService(intent, mConn, Context.BIND_AUTO_CREATE);
            }
        });

        cmdNewBmp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound)
                {
                    //mService.newBmp();
                }
            }
        });

        cmdEnrollTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound)
                {
                    new EnrollTester().start();
                }
            }
        });

        cmdRecogTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound)
                {
                    new RecogTester().start();
                }
            }
        });
    }

    class RecogTester extends Thread
    {
        public void run()
        {
            //we repeat 10 times
            for(int i = 0; i < 10; i++)
            {
                doRecogTest();
            }
        }

        void doRecogTest()
        {
            for(int dbSize = 1; dbSize <= 5; dbSize++)
            {
                Log.i(TAG, "dbSize: " + dbSize);
                //first setup the recognition database
                //with upto "dbSize" number of faces
                for(int i = 0; i < dbSize - 1; i++)
                {
                    enrollSingleImage(i);
                }

                enrollSingleImage(4); //4 is a version of "ref"

                //now do a recognition operation
                Bitmap test;
                if(DEBUG_REF)
                    test = mService.ds.getRef();
                else
                    test = mService.ds.getNonRef();

                mService.newBmp(test, -1, CamSoda.OPCODE_RECOG);

                //FRDCSoda will have already reset the recognition db
            }
        }
    }

    class EnrollTester extends Thread
    {
        public void run()
        {
            for(int j = 0; j < 10; j++) {
                enrollAllImages();
            }
        }
    }

    public void enrollSingleImage(int index)
    {
        Bitmap _bmp = mService.ds.getBitmap(index);
        mService.newBmp(_bmp, -1, CamSoda.OPCODE_ENROLL);
    }

    public void enrollAllImages()
    {
        for (int i = 0; i < mService.numImages; i++) {
            Bitmap _bmp = mService.ds.getBitmap(i);
            mService.newBmp(_bmp, -1, CamSoda.OPCODE_ENROLLTEST);
        }
    }

    private ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CamInjector.CamBinder binder = (CamInjector.CamBinder) service;
            mService = binder.getService();
            mBound = true;

            showToast("connected to CamInjector");

            mService.init();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;

            showToast("disconnected from CamInjector");
        }
    };

    public void showToast(String msg)
    {
        Toast t = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        t.show();
    }
}
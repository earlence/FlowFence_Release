package edu.umich.oasis.study.frdc;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.qualcomm.snapdragon.sdk.face.FaceData;
import com.qualcomm.snapdragon.sdk.face.FacialProcessing;

import java.util.ArrayList;
import java.util.EnumSet;

/*
ATTENTION!!
When running "bench recog", make sure to set ENROLL_TEST = true
*/

public class FRDCMain extends Activity implements IImageCallback {

    private static final String TAG = "FRDCMain";

    Button cmdTakePic, cmdEnroll, cmdRecog, cmdSave, cmdTestUnlock, cmdReset;
    Button cmdBenchEnroll, cmdBenchRecog, cmdSavePic;

    EditText txtResult, txtDB;

    CameraAPI capi;
    Bitmap facePic = null;
    static FacialProcessing faceObj;

    static ArrayList<Integer> validPeronsForUnlock;

    private static final String TIMING_TAG_ENROLL = "FRDC_ENROLL";
    private static final String TIMING_TAG_RECOG = "FRDC_RECOG";
    private static final boolean DEBUG_TIMING = true;
    static long start, end;

    static DataSource ds;

    private final int numImages = 5;

    private static boolean ENROLL_TEST = false;
    private static boolean REF_TEST = false;

    static
    {
        boolean isQualcommSDKSupported = FacialProcessing.isFeatureSupported(FacialProcessing.FEATURE_LIST.FEATURE_FACIAL_RECOGNITION);

        if(isQualcommSDKSupported) {
            Log.i(TAG, "FSDK supported");

            faceObj = FacialProcessing.getInstance();
            if(faceObj != null)
            {
                faceObj.setRecognitionConfidence(58);
                faceObj.setProcessingMode(FacialProcessing.FP_MODES.FP_MODE_STILL);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frdcmain);

        //initFSDK();
        capi = new CameraAPI(getApplicationContext(), this);
        SmartThingsService.getInstance();

        cmdTakePic = (Button) findViewById(R.id.cmdTakePic);
        cmdEnroll = (Button) findViewById(R.id.cmdEnroll);
        cmdRecog = (Button) findViewById(R.id.cmdRecog);
        //cmdSave = (Button) findViewById(R.id.cmdSaveAlbum);
        //txtResult = (EditText) findViewById(R.id.txtResult);
        //cmdTestUnlock = (Button) findViewById(R.id.cmdTestUnlock);
        //cmdReset = (Button) findViewById(R.id.cmdReset);

        //cmdBenchEnroll = (Button) findViewById(R.id.cmdBenchEnroll);
        //cmdBenchRecog = (Button) findViewById(R.id.cmdBenchRecog);
        //txtDB = (EditText) findViewById(R.id.txtDB);

        //txtDB.setText("2");

        cmdSavePic = (Button) findViewById(R.id.cmdSavePic);

        validPeronsForUnlock = new ArrayList<Integer>();

        ds = new DataSource(numImages);


        cmdTakePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                capi.showCameraPreview();
            }
        });

        cmdEnroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(facePic != null)
                {
                    enrollPerson(facePic);
                }

            }
        });

        cmdRecog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(facePic != null)
                {
                    recognizePerson(facePic);
                }
            }
        });

        /*cmdSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        cmdTestUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doUnlockOnFrontDoor();
            }
        });

        cmdReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetAlbum();
            }
        });

        cmdBenchEnroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                clearLog();
                new BenchEnrollRunner().start();

            }
        });

        cmdBenchRecog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                clearLog();

                ds.loadFaces();

                int dbSize = Integer.parseInt(txtDB.getText().toString());
                new BenchRecogRunner().start();

            }
        });

        cmdSavePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //save facePic to filename given by txtDB
                String indexName = txtDB.getText().toString();
                new ImageSaver(indexName).start();
            }
        });*/
    }

    /*class ImageSaver extends Thread
    {
        String fn;

        public ImageSaver(String name)
        {
            fn = name;
        }

        public void run()
        {
            if(facePic != null)
            {
                ds.writeBitmap(facePic, fn);
                facePic = null;
                showToast(fn + ".jpg saved");
            }
        }
    }*/

    class BenchEnrollRunner extends Thread
    {
        public void run()
        {
            for(int i = 0; i < numImages; i++) {
                Bitmap bmp = ds.getBitmap(i);
                benchEnrollPersonSync(bmp);
            }
        }
    }

    class BenchRecogRunner extends Thread
    {
        public BenchRecogRunner()
        {
        }

        public void run()
        {
            for(int i = 0; i < 10; i++)
            {
                doRecogTest();

                validPeronsForUnlock.clear();
            }
        }

        public void doRecogTest()
        {
            for(int dbSize = 1; dbSize <= 5; dbSize++) {

                Log.i(TIMING_TAG_RECOG, "dbSize: " + dbSize);

                //enroll the database size - 1, and then enroll a version of the ref image at the end
                for (int i = 0; i < dbSize - 1; i++) {
                    Bitmap bmp = ds.faces.get(i);
                    benchEnrollPersonSync(bmp);
                }
                //4.jpg is a version of the reference image.
                //it should always be the last image in the database
                benchEnrollPersonSync(ds.faces.get(4));

                //two test types.
                //1. the testFace is one of the enrolled faces, but slightly different coz its a different image, but of the same person
                //2. the testFace is not one of the enrolled faces.

                Bitmap testFace;
                if (REF_TEST)
                    testFace = ds.ref;
                else
                    testFace = ds.nonref;

                //run a recognition task
                benchRecogSync(testFace);

                resetAlbum();
                validPeronsForUnlock.clear();
            }
        }
    }

    private void initFSDK()
    {
        boolean isQualcommSDKSupported = FacialProcessing.isFeatureSupported(FacialProcessing.FEATURE_LIST.FEATURE_FACIAL_RECOGNITION);

        if(isQualcommSDKSupported) {
            Log.i(TAG, "FSDK supported");

            faceObj = FacialProcessing.getInstance();
            if(faceObj != null)
            {
                faceObj.setRecognitionConfidence(58);
                faceObj.setProcessingMode(FacialProcessing.FP_MODES.FP_MODE_STILL);
            }
        }
    }

    public void onPicture(Bitmap bmp)
    {
        Log.i(TAG, "pic size:" + bmp.getByteCount());
        facePic = bmp;
    }

    // Used for benchmarking. This will reset the entire album after enrolling
    static void benchEnrollPersonSync(Bitmap bmp)
    {
        if(DEBUG_TIMING)
        {
            start = SystemClock.uptimeMillis();
        }

        boolean addok = faceObj.setBitmap(bmp);
        if (addok) {
            FaceData[] faceArray = faceObj.getFaceData();

            if (faceArray != null) {
                log("number of faces: " + faceArray.length);

                //we only take 1 face, the first one.
                int personId = faceObj.addPerson(0);
                log("New person add with id: " + personId);

                validPeronsForUnlock.add(personId);

            } else
                log("no face detected in bmp");
        } else
            log("error setBitmap");

        if(DEBUG_TIMING)
        {
            end = SystemClock.uptimeMillis();
            Log.i(TIMING_TAG_ENROLL, "" + (end - start));
        }

        if(ENROLL_TEST)
            resetAlbum();
    }

    void enrollPerson(Bitmap bmp)
    {
        final Bitmap _bmp = bmp;

        new Thread() {
            public void run() {

                if(DEBUG_TIMING)
                {
                    start = SystemClock.uptimeMillis();
                }

                boolean addok = faceObj.setBitmap(_bmp);
                if (addok) {
                    FaceData[] faceArray = faceObj.getFaceData();

                    if (faceArray != null) {
                        log("number of faces: " + faceArray.length);

                        //we only take 1 face, the first one.
                        int personId = faceObj.addPerson(0);
                        log("New person add with id: " + personId);

                        validPeronsForUnlock.add(personId);

                    } else
                        log("no face detected in bmp");
                } else
                    log("error setBitmap");

                if(DEBUG_TIMING)
                {
                    end = SystemClock.uptimeMillis();
                    Log.i(TIMING_TAG_ENROLL, "" + (end - start));
                }
            }
        }.start();
    }

    static void benchRecogSync(Bitmap bmp)
    {
        if(DEBUG_TIMING)
        {
            start = SystemClock.uptimeMillis();
        }

        boolean addok = faceObj.setBitmap(bmp);
        if (addok)
        {
            FaceData[] faceArray = faceObj.getFaceData(EnumSet.of(FacialProcessing.FP_DATA.FACE_IDENTIFICATION));
            StringBuffer sb = new StringBuffer();
            if (faceArray != null) {
                for (int i = 0; i < faceArray.length; i++) {
                    int personId, confidence;
                    personId = faceArray[i].getPersonId();
                    confidence = faceArray[i].getRecognitionConfidence();

                    sb.append("personId: " + personId + ", confid.:" + confidence + "\n");

                    if(isValidPerson(personId))
                    {
                        log("valid person, attempting unlock");
                        //doUnlockOnFrontDoor();

                    }
                }

                log(sb.toString());


            } else
                log("No faces found in bitmap");
        } else
            log("error recog setBitmap");

        if(DEBUG_TIMING)
        {
            end = SystemClock.uptimeMillis();
            Log.i(TIMING_TAG_RECOG, "" + (end - start));
        }
    }

    void recognizePerson(Bitmap bmp)
    {
        final Bitmap _bmp = bmp;
        new Thread() {

            public void run() {

                if(DEBUG_TIMING)
                {
                    start = SystemClock.uptimeMillis();
                }

                boolean addok = faceObj.setBitmap(_bmp);
                if (addok)
                {
                    FaceData[] faceArray = faceObj.getFaceData(EnumSet.of(FacialProcessing.FP_DATA.FACE_IDENTIFICATION));
                    StringBuffer sb = new StringBuffer();
                    if (faceArray != null) {
                        for (int i = 0; i < faceArray.length; i++) {
                            int personId, confidence;
                            personId = faceArray[i].getPersonId();
                            confidence = faceArray[i].getRecognitionConfidence();

                            sb.append("personId: " + personId + ", confid.:" + confidence + "\n");

                            if(isValidPerson(personId))
                            {
                                log("valid person, attempting unlock");
                                doUnlockOnFrontDoor();
                            }
                        }

                        log(sb.toString());

                    } else
                        log("No faces found in bitmap");
                } else
                    log("error recog setBitmap");

                if(DEBUG_TIMING)
                {
                    end = SystemClock.uptimeMillis();
                    Log.i(TIMING_TAG_RECOG, "" + (end - start));
                }
            }
        }.start();
    }

    static boolean isValidPerson(int pid)
    {
        for(Integer i : validPeronsForUnlock)
        {
            if(i == pid)
                return true;
        }

        return false;
    }

    private void saveAlbum()
    {
        byte [] albumData = faceObj.serializeRecogntionAlbum();
    }

    private static void resetAlbum()
    {
        if(faceObj != null)
        {
            boolean res = faceObj.resetAlbum();

        }
    }

    private void doUnlockOnFrontDoor()
    {
        ArrayList<SmartDevice> locks = SmartThingsService.getInstance().getLocks();

        if(locks.size() >= 1) {
            log("there is atleast 1 lock: " + locks.get(0).getName());
            SmartThingsService.getInstance().lockUnlock("unlock", locks.get(0).getId());
        }
    }

    private void showToast(String text)
    {
        final String _text = text;

        FRDCMain.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(getApplicationContext(), _text, Toast.LENGTH_SHORT);
                t.show();
            }
        });

    }

    private static void log(String text)
    {
        //final String _text = text;

        /*FRDCMain.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtResult.append(_text + "\n");
            }
        });*/

        Log.i("UI", text);
    }

    private void clearLog()
    {
        FRDCMain.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtResult.setText("");
            }
        });
    }
}

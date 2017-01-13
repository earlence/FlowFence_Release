/*
 * Copyright (C) 2017 The Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umich.oasis.study.fencedfrdc;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;

import com.qualcomm.snapdragon.sdk.face.FaceData;
import com.qualcomm.snapdragon.sdk.face.FacialProcessing;

import java.util.ArrayList;
import java.util.EnumSet;

/**
 *
 * Face Recognition -> Door Unlock
 */
public class FRDCSoda implements Parcelable
{
    private static final String TAG = "FRDCSoda";

    //non-state
    static FacialProcessing faceObj;
    static ArrayList<Bitmap> testFaces = new ArrayList<Bitmap>();
    static Bitmap ref = null;
    static Bitmap nonref = null;

    //book-keeping, non-state
    static boolean DEBUG_TIMING = true;
    static boolean ENROLL_TEST = true;
    static boolean REF_TEST = false;

    private static final String TIMING_TAG_ENROLL = "FRDC_ENROLL";
    private static final String TIMING_TAG_RECOG = "FRDC_RECOG";
    static long start, end;
    static ArrayList<Integer> validPeronsForUnlock = new ArrayList<Integer>();

    //!!! keep in sync with CamSoda.java !!!
    public static final int OPCODE_ENROLL = 1;
    public static final int OPCODE_ENROLLTEST = 2;
    public static final int OPCODE_ENROLL_REF = 3;
    public static final int OPCODE_ENROLL_NONREF = 4;
    public static final int OPCODE_RECOG = 5;

    public FRDCSoda()
    {
    }

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

    public static void bmpRx(int opcode, int index, Bitmap bmp, long deliveryTime)
    {
        if(opcode == OPCODE_ENROLLTEST)
        {
            //enroll the "bmp", and output a latency value
            benchEnrollPersonSync(bmp);

            long endL = SystemClock.uptimeMillis();
            double latencyInst = ((double) (endL - deliveryTime));
            Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

            resetAlbum();
        }
        else if(opcode == OPCODE_ENROLL)
        {
            benchEnrollPersonSync(bmp);
        }
        /*else if(opcode == OPCODE_ENROLL_REF)
        {
            ref = bmp;
        }
        else if(opcode == OPCODE_ENROLL_NONREF)
        {
            nonref = bmp;
        }*/
        else if(opcode == OPCODE_RECOG)
        {
            //this assumes the "recognition db" has been setup using OPCODE_ENROLL previously
            benchRecogSync(bmp);

            long endL = SystemClock.uptimeMillis();
            double latencyInst = ((double) (endL - deliveryTime));
            Log.i("LATENCY", String.valueOf(latencyInst)); //in ms

            validPeronsForUnlock.clear();
            resetAlbum();
        }
    }

    /*public static void _bmpRx(int opcode, int index, Bitmap bmp, long eventFireTime)
    {
        if(opcode == 1) //bitmaps are coming in. store in arraylist.
        {
            testFaces.add(index, bmp);
        }
        else if(opcode == 2) //the ref image
        {
            ref = bmp;
        }
        else if(opcode == 3)
        {
            nonref = bmp;
        }

        //this does our "enrollment" benchmark
        //the moment 5 faces are delivered to us, we start
        if(testFaces.size() == 5)
        {
            //doEnrollTest();

            //repeat 10 times
            for(int i = 0; i < 10; i++) {
                doRecogTest();

                //reset ourselves!
                //testFaces.clear();
                validPeronsForUnlock.clear();
            }
        }
    }

    static void doEnrollTest()
    {
        //begin enrolling all faces
        for(int i = 0; i < 10; i++) {
            for (Bitmap aFace : testFaces) {
                benchEnrollPersonSync(aFace);
            }
        }
    }

    static void doRecogTest()
    {
        //vary database size from 1 to 5
        for(int dbSize = 1; dbSize <= 5; dbSize++)
        {
            Log.i(TIMING_TAG_RECOG, "dbSize == " + dbSize);

            //enroll the database size - 1, and then enroll a version of the ref image at the end
            for(int i = 0; i < dbSize - 1; i++)
            {
                Bitmap bmp = testFaces.get(i);
                benchEnrollPersonSync(bmp);
            }
            //4.jpg is a version of the reference image.
            //it should always be the last image in the database
            benchEnrollPersonSync(testFaces.get(4));

            //two test types.
            //1. the testFace is one of the enrolled faces, but slightly different coz its a different image, but of the same person
            //2. the testFace is not one of the enrolled faces.

            Bitmap testFace;
            if(REF_TEST)
                testFace = ref;
            else
                testFace = nonref;

            //run a recognition task
            benchRecogSync(testFace);

            resetAlbum();
        }
    }*/

    static void benchRecogSync(Bitmap bmp)
    {
        /*if(DEBUG_TIMING)
        {
            start = SystemClock.uptimeMillis();
        }*/

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

        /*if(DEBUG_TIMING)
        {
            end = SystemClock.uptimeMillis();
            Log.i(TIMING_TAG_RECOG, "" + (end - start));
        }*/
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

    // Used for benchmarking. This will reset the entire album after enrolling
    static void benchEnrollPersonSync(Bitmap bmp)
    {
        /*if(DEBUG_TIMING)
        {
            start = SystemClock.uptimeMillis();
        }*/

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

        /*if(DEBUG_TIMING)
        {
            end = SystemClock.uptimeMillis();
            Log.i(TIMING_TAG_ENROLL, "" + (end - start));
        }*/

        /*if(ENROLL_TEST)
            resetAlbum();*/
    }

    static void resetAlbum()
    {
        if(faceObj != null)
        {
            boolean res = faceObj.resetAlbum();
            if(!res)
                log("Error resetting album");
        }
    }

    static void log(String msg)
    {
        Log.i(TAG, msg);
    }


    //use "enroll", and "recog" for standard app operation
    //the other methods are for benchmarking purposes
    static void enroll(Bitmap bmp)
    {
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
    }

    static void recog(Bitmap bmp)
    {
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
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {

    }

    public static final Parcelable.Creator<FRDCSoda> CREATOR = new Parcelable.Creator<FRDCSoda>()
    {
        public FRDCSoda createFromParcel(Parcel in) {
            return new FRDCSoda(in);
        }

        public FRDCSoda[] newArray(int size) {
            return new FRDCSoda[size];
        }
    };

    private FRDCSoda(Parcel in)
    {

    }
}

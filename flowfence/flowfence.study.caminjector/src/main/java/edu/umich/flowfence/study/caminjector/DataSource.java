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

package edu.umich.flowfence.study.caminjector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;

public class DataSource
{
    private String basePath;
    private int counter;
    int numImages;

    private static final String TAG = "BitmapDataSource";

    public DataSource(int num)
    {
        basePath = Environment.getExternalStorageDirectory() + "/frdc_images/";
        counter = 0;
        numImages = num;
    }

    public Bitmap nextBmp()
    {
        String filename = basePath + counter + ".jpg";
        counter = (counter + 1) % numImages;

        return BitmapFactory.decodeFile(filename);
    }

    public Bitmap getBitmap(int index)
    {
        String filename = basePath + index + ".jpg";

        return BitmapFactory.decodeFile(filename);
    }

    public Bitmap getRef()
    {
        String filename = basePath + "refe.jpg";

        return BitmapFactory.decodeFile(filename);
    }

    public Bitmap getNonRef()
    {
        String filename = basePath + "nonref.jpg";

        return BitmapFactory.decodeFile(filename);
    }

    public void writeBitmap(Bitmap bmp, String index)
    {
        String filename = basePath + index + ".jpg";
        try {
            FileOutputStream fout = new FileOutputStream(filename);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fout);
            fout.flush();
            fout.close();
        } catch(Exception e)
        {
            Log.e("DataSource", e.toString());
        }

    }
}

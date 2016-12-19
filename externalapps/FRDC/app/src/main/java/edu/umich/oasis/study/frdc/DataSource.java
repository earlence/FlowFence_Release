package edu.umich.oasis.study.frdc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Created by earlence on 2/3/16.
 */
public class DataSource
{
    private String basePath;
    private int counter;
    int numImages;

    public static Bitmap ref, nonref;

    public static ArrayList<Bitmap> faces = new ArrayList<Bitmap>();

    public DataSource(int num)
    {
        basePath = Environment.getExternalStorageDirectory() + "/frdc_images/";
        counter = 0;
        numImages = num;
    }

    public void loadFaces()
    {
        faces.clear();

        for(int i = 0; i < numImages; i++)
        {
            Bitmap _bmp = getBitmap(i);
            faces.add(i, _bmp);
        }

        ref = getRef();
        nonref = getNonRef();
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

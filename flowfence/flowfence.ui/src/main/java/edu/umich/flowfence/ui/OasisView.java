package edu.umich.flowfence.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;

public class OasisView
{
    Bitmap bmp;
    Canvas canvas;
    View view;
    Object mLock;

    public OasisView(int width, int height, View v)
    {
        bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);
        view = v;


        mLock = new Object();
    }

    public Bitmap pullRedrawnView()
    {
        Bitmap ret;
        synchronized(mLock)
        {
            ret = bmp;
        }

        return ret;
    }

    public void drawView()
    {
        synchronized(mLock)
        {
            if(bmp != null)
            {
                int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

                canvas.translate(-view.getScrollX(), -view.getScrollY());

                view.measure(spec, spec);
                view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                view.draw(canvas);
            }
        }
    }

    public void dispatchClick()
    {
        synchronized(mLock)
        {
            view.performClick();
        }
    }

    public void dispatchTouchEvent(MotionEvent me)
    {
        synchronized(mLock)
        {
            view.dispatchTouchEvent(me);
        }
    }

    public void recycle()
    {
        synchronized(mLock)
        {
            if(bmp != null)
            {
                bmp.recycle();
                bmp = null;
            }
        }
    }
}

package com.eightbitlab.blurview_sample;

import static java.lang.Thread.sleep;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SurfaceViewFragment extends BaseFragment {
    @Override
    int getLayoutId() {
        return R.layout.fragment_surfaceview;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
    }


    private Bitmap[] bitmaps = new Bitmap[3];

    SurfaceView surfaceView;

    public void init() {

        surfaceView = getView().findViewById(R.id.surfaceView);

        Bitmap bitmap1 = BitmapFactory.decodeResource(getResources(), R.mipmap.t1);
        Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(), R.mipmap.t2);
        Bitmap bitmap3 = BitmapFactory.decodeResource(getResources(), R.mipmap.t3);
        bitmaps[0] = bitmap1;
        bitmaps[1] = bitmap2;
        bitmaps[2] = bitmap3;

        startLoopDrawToSurfaceView();

    }


    boolean loopFlag = false;

    public void startLoopDrawToSurfaceView() {

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        RectF rectF = new RectF(0, 0, screenWidth, screenHeight);

        loopFlag = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (loopFlag) {
                    for (int i = 0; i < bitmaps.length; i++) {
                        final Bitmap bitmap = bitmaps[i];
                        if (getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                SurfaceHolder holder = surfaceView.getHolder();
                                boolean valid = holder.getSurface().isValid();
                                if (!valid) {
                                    return;
                                }
                                Canvas canvas = holder.lockCanvas();
                                if (canvas != null) {
                                    canvas.drawBitmap(bitmap, null, rectF, null);
                                }
                                holder.unlockCanvasAndPost(canvas);
                            }
                        });
                        try {
                            sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loopFlag = false;
    }
}

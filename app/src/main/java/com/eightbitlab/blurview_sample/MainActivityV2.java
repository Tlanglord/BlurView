package com.eightbitlab.blurview_sample;

import static java.lang.Thread.sleep;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.google.android.material.tabs.TabLayout;

import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class MainActivityV2 extends AppCompatActivity {

    private TabLayout tabLayout;
    private BlurView bottomBlurView;
    private BlurView topBlurView;
    private SeekBar radiusSeekBar;
    private View root;
    private SurfaceView surfaceView;

    public static void start(Context context) {
        Intent starter = new Intent(context, MainActivityV2.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_v2);
        initView();
        setupBlurView();
        init();
    }

    private void initView() {
        tabLayout = findViewById(R.id.tabLayout);
        bottomBlurView = findViewById(R.id.bottomBlurView);
        topBlurView = findViewById(R.id.topBlurView);
        // Rounded corners + casting elevation shadow with transparent background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            topBlurView.setClipToOutline(true);
            topBlurView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    topBlurView.getBackground().getOutline(outline);
                    outline.setAlpha(1f);
                }
            });
        }
        radiusSeekBar = findViewById(R.id.radiusSeekBar);

        surfaceView = findViewById(R.id.v_surface);

        root = surfaceView;
    }

    private void setupBlurView() {
        final float radius = 25f;
        final float minBlurRadius = 4f;
        final float step = 4f;

        //set background, if your root layout doesn't have one
        final Drawable windowBackground = getWindow().getDecorView().getBackground();
        BlurAlgorithm algorithm = getBlurAlgorithm();

//        topBlurView.setupWith(root, algorithm)
//                .setFrameClearDrawable(windowBackground)
//                .setBlurRadius(radius);

        bottomBlurView.setupWith(root, new RenderScriptBlur(this))
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius);

        int initialProgress = (int) (radius * step);
        radiusSeekBar.setProgress(initialProgress);

        radiusSeekBar.setOnSeekBarChangeListener(new SeekBarListenerAdapter() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float blurRadius = progress / step;
                blurRadius = Math.max(blurRadius, minBlurRadius);
                topBlurView.setBlurRadius(blurRadius);
                bottomBlurView.setBlurRadius(blurRadius);
            }
        });
    }

    @NonNull
    private BlurAlgorithm getBlurAlgorithm() {
        BlurAlgorithm algorithm;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            algorithm = new RenderEffectBlur();
        } else {
            algorithm = new RenderScriptBlur(this);
        }
        return algorithm;
    }

    static class ViewPagerAdapter extends FragmentPagerAdapter {

        ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            return Page.values()[position].getFragment();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return Page.values()[position].getTitle();
        }

        @Override
        public int getCount() {
            return Page.values().length;
        }
    }

    enum Page {
        FIRST("ScrollView") {
            @Override
            Fragment getFragment() {
                return new ScrollFragment();
            }
        },
        SECOND("RecyclerView") {
            @Override
            Fragment getFragment() {
                return new ListFragment();
            }
        },
        THIRD("Static") {
            @Override
            Fragment getFragment() {
                return new ImageFragment();
            }
        },
        FOUR("SURFACE") {
            @Override
            Fragment getFragment() {
                return new SurfaceViewFragment();
            }
        };

        private String title;

        Page(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }

        abstract Fragment getFragment();
    }

    private Bitmap[] bitmaps = new Bitmap[3];


    public void init() {

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

        float screenWidth = getResources().getDisplayMetrics().widthPixels;
//        int screenHeight = getResources().getDisplayMetrics().heightPixels;


        int height = 800;

        RectF f = new RectF(0, 0, screenWidth, height);

        Paint paint = new Paint();
        paint.setFilterBitmap(true);


        loopFlag = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (loopFlag) {
                    for (int i = 0; i < bitmaps.length; i++) {
                        final Bitmap bitmap = bitmaps[i];
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                SurfaceHolder holder = surfaceView.getHolder();
                                boolean valid = holder.getSurface().isValid();
                                if (!valid) {
                                    return;
                                }
                                Canvas canvas = holder.lockCanvas();

                                float width = bitmap.getWidth() * 1f;
                                float height = bitmap.getHeight() * 1f;

                                float scale = width / screenWidth;

                                float nHeight = height / scale;

                                f.set(0, 0, screenWidth, nHeight);

                                if (canvas != null) {
                                    canvas.drawBitmap(bitmap, null, f, paint);
                                }
                                holder.unlockCanvasAndPost(canvas);
                            }
                        });
                        try {
                            sleep(2000);
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

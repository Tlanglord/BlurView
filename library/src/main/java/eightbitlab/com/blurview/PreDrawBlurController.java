package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Blur Controller that handles all blur logic for the attached View.
 * It honors View size changes, View animation and Visibility changes.
 * <p>
 * The basic idea is to draw the view hierarchy on a bitmap, excluding the attached View,
 * then blur and draw it on the system Canvas.
 * <p>
 * It uses {@link ViewTreeObserver.OnPreDrawListener} to detect when
 * blur should be updated.
 * <p>
 */
public final class PreDrawBlurController implements BlurController {

    private static final String TAG = "PreDrawBlurController";

    @ColorInt
    public static final int TRANSPARENT = 0;

    private float blurRadius = DEFAULT_BLUR_RADIUS;

    private final BlurAlgorithm blurAlgorithm;
    private BlurViewCanvas internalCanvas;
    private Bitmap internalBitmap;

    @SuppressWarnings("WeakerAccess")
    final View blurView;
    private int overlayColor;
    private final View rootView;
    private final int[] rootLocation = new int[2];
    private final int[] blurViewLocation = new int[2];

    private final ViewTreeObserver.OnPreDrawListener drawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {

//            Log.d(TAG, "onPreDraw: start");
            // Not invalidating a View here, just updating the Bitmap.
            // This relies on the HW accelerated bitmap drawing behavior in Android
            // If the bitmap was drawn on HW accelerated canvas, it holds a reference to it and on next
            // drawing pass the updated content of the bitmap will be rendered on the screen

            boolean hasSurface = hasSurfaceView();

            if (hasSurface) {
                long nowTime = System.currentTimeMillis();
                long cost = nowTime - lockTime;
                Log.d(TAG, "onPreDraw: cost = " + cost + "ms");
                if (cost > 60) {
                    updateBlur();
                } else {
                    long delay = 60 - cost;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "onPreDraw: delay = " + delay + "ms");
                            updateBlur();
                        }
                    }, delay);
                }
            } else {
                updateBlur();
            }

//            Log.d(TAG, "onPreDraw: end");
            return true;
        }
    };

    private boolean blurEnabled = true;
    private boolean initialized;

    @Nullable
    private Drawable frameClearDrawable;

    private SurfaceView surfaceView;

    /**
     * @param blurView  View which will draw it's blurred underlying content
     * @param rootView  Root View where blurView's underlying content starts drawing.
     *                  Can be Activity's root content layout (android.R.id.content)
     * @param algorithm sets the blur algorithm
     */
    public PreDrawBlurController(@NonNull View blurView, @NonNull View rootView, @ColorInt int overlayColor, BlurAlgorithm algorithm) {
        this.rootView = rootView;
        this.blurView = blurView;
        this.overlayColor = overlayColor;
        this.blurAlgorithm = algorithm;
        if (algorithm instanceof RenderEffectBlur) {
            // noinspection NewApi
            ((RenderEffectBlur) algorithm).setContext(blurView.getContext());
        }

        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        surfaceView = findSurfaceView(rootView);

        if (hasSurfaceView()) {
            startLooper();
        }

        Log.d(TAG, "PreDrawBlurController: measuredWidth = " + measuredWidth + ", measuredHeight = " + measuredHeight);

        init(measuredWidth, measuredHeight);
    }


    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean looperStarted = false;


    private void startLooper() {
        looperStarted = true;

//        blurView.postInvalidate();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                Log.d(TAG, "startLooper: run , looperStarted=" + looperStarted);

                if (!looperStarted) {
                    return;
                }


                updateBlur();
                handler.postDelayed(this, 100);
            }
        }, 1000);

    }

    private boolean hasSurfaceView() {
        return surfaceView != null;
    }

    private SurfaceView findSurfaceView(View rootView) {
        if (rootView instanceof SurfaceView) {
            return (SurfaceView) rootView;
        }

        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof SurfaceView) {
                    return (SurfaceView) child;
                } else if (child instanceof ViewGroup) {
                    SurfaceView surfaceView = findSurfaceView(child);
                    if (surfaceView != null) {
                        return surfaceView;
                    }
                }
            }
        }

        return null;
    }

    @SuppressWarnings("WeakerAccess")
    void init(int measuredWidth, int measuredHeight) {
        setBlurAutoUpdate(true);
        SizeScaler sizeScaler = new SizeScaler(blurAlgorithm.scaleFactor());
        if (sizeScaler.isZeroSized(measuredWidth, measuredHeight)) {
            // Will be initialized later when the View reports a size change
            blurView.setWillNotDraw(true);
            return;
        }


        blurView.setWillNotDraw(false);
        SizeScaler.Size bitmapSize = sizeScaler.scale(measuredWidth, measuredHeight);
        Log.d(TAG, "init: bitmapSize = " + bitmapSize.width + ", " + bitmapSize.height);
        internalBitmap = Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, blurAlgorithm.getSupportedBitmapConfig());
        internalCanvas = new BlurViewCanvas(internalBitmap);
        initialized = true;
        // Usually it's not needed, because `onPreDraw` updates the blur anyway.
        // But it handles cases when the PreDraw listener is attached to a different Window, for example
        // when the BlurView is in a Dialog window, but the root is in the Activity.
        // Previously it was done in `draw`, but it was causing potential side effects and Jetpack Compose crashes
        updateBlur();
    }

    Paint paint = new Paint();

    {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
//        paint.setStrokeWidth(20);
        paint.setTextSize(20);
        paint.setFilterBitmap(true);
    }

    @SuppressWarnings("WeakerAccess")
    void updateBlur() {


        if (!blurEnabled || !initialized) {
            return;
        }

        if (frameClearDrawable == null) {
//            internalBitmap.eraseColor(Color.TRANSPARENT);
        } else {
//            frameClearDrawable.draw(internalCanvas);
        }

        if (hasSurfaceView()) {

            int rvw = surfaceView.getWidth();
            int rvh = surfaceView.getHeight();


            new Thread() {
                @Override
                public void run() {


//                    Log.d(TAG, "updateBlur: rvw = " + rvw + ", rvh = " + rvh);

                    Bitmap bitmap = Bitmap.createBitmap(rvw, rvh, Bitmap.Config.ARGB_8888);
                    RectF destF = new RectF(0, 0, blurView.getWidth(), blurView.getHeight());
                    boolean valid = surfaceView.getHolder().getSurface().isValid();

                    if (!valid) {
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        long time = System.currentTimeMillis();
                        Log.d(TAG, "PixelCopy: start");
                        PixelCopy.request(surfaceView, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                            @Override
                            public void onPixelCopyFinished(int copyResult) {
                                if (copyResult == PixelCopy.SUCCESS) {
                                    looperStarted = false;

                                    Log.d(TAG, "onPixelCopyFinished: end , cost = " + (System.currentTimeMillis() - time) + "ms");
                                    internalCanvas.save();
                                    setupInternalCanvasMatrix();
//                                    internalCanvas.drawColor(Color.RED);
                                    internalCanvas.drawBitmap(bitmap, 0, 0, paint);
//                                    internalCanvas.drawText("HAHAHAHA", 0, 400, paint);
                                    internalCanvas.restore();
                                    blurAndSave();
                                    postLock();

                                    Log.d(TAG, "PixelCopy: end , success ");
                                } else {
                                    Log.e(TAG, "Failed to copyPixels: " + copyResult);
                                }
                            }
                        }, new Handler(Looper.getMainLooper()));
                    }
                }
            }.start();

        } else {

            Log.d(TAG, "updateBlur start");

            internalCanvas.save();
            setupInternalCanvasMatrix();
            rootView.draw(internalCanvas);
            internalCanvas.restore();

            blurAndSave();

            Log.d(TAG, "updateBlur end");

        }

    }


    private long lockTime = 0;

    private void postLock() {

        lockTime = System.currentTimeMillis();
        // 30ms调用一次

        blurView.postInvalidate();
    }

    /**
     * Set up matrix to draw starting from blurView's position
     */
    private void setupInternalCanvasMatrix() {
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);

        int left = blurViewLocation[0] - rootLocation[0];
        int top = blurViewLocation[1] - rootLocation[1];

        // https://github.com/Dimezis/BlurView/issues/128
        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();

        float scaledLeftPosition = -left / scaleFactorW;
        float scaledTopPosition = -top / scaleFactorH;

        internalCanvas.translate(scaledLeftPosition, scaledTopPosition);
        internalCanvas.scale(1 / scaleFactorW, 1 / scaleFactorH);
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!blurEnabled || !initialized) {
            return true;
        }
        // Not blurring itself or other BlurViews to not cause recursive draw calls
        // Related: https://github.com/Dimezis/BlurView/issues/110
        if (canvas instanceof BlurViewCanvas) {
            return false;
        }

        // https://github.com/Dimezis/BlurView/issues/128
        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();

        canvas.save();
        canvas.scale(scaleFactorW, scaleFactorH);
        blurAlgorithm.render(canvas, internalBitmap);
        canvas.restore();
//        if (overlayColor != TRANSPARENT) {
//            canvas.drawColor(overlayColor);
//        }
        return true;
    }

    private void blurAndSave() {
        internalBitmap = blurAlgorithm.blur(internalBitmap, blurRadius);
        if (!blurAlgorithm.canModifyBitmap()) {
            internalCanvas.setBitmap(internalBitmap);
        }
    }

    @Override
    public void updateBlurViewSize() {


        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        Log.d(TAG, "updateBlurViewSize: measuredWidth = " + measuredWidth + ", measuredHeight = " + measuredHeight);

        init(measuredWidth, measuredHeight);
    }

    @Override
    public void destroy() {
        setBlurAutoUpdate(false);
        blurAlgorithm.destroy();
        initialized = false;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        return this;
    }

    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        setBlurAutoUpdate(enabled);
        blurView.invalidate();
        return this;
    }

    public BlurViewFacade setBlurAutoUpdate(final boolean enabled) {
        rootView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            rootView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            // Track changes in the blurView window too, for example if it's in a bottom sheet dialog
            if (rootView.getWindowId() != blurView.getWindowId()) {
                blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            }
        }
        return this;
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            blurView.invalidate();
        }
        return this;
    }
}

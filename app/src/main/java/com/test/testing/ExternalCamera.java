package com.test.testing;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.test.faceopen.R;
import com.test.testing.env.BorderedText;
import com.test.testing.env.ImageUtils;
import com.test.testing.tracking.MultiBoxTracker;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ExternalCamera extends AppCompatActivity implements UVCCameraHelper.OnMyDevConnectListener, CameraDialog.CameraDialogParent, CameraViewInterface.Callback, AbstractUVCCameraHandler.OnPreViewResultListener {



    private static final float TEXT_SIZE_DIP = 10;
    public ImageView i;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    OverlayView trackingOverlay;
    private Classifier classifier;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private byte[] luminanceCopy;
    private BorderedText borderedText;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    @BindView(R.id.externalCamera)
    public View textureViewUsb;

    String TAG = "External_cam_testing";

    private boolean isRequest;
    private boolean isPreview;
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"};

    public static void verifyStoragePermissions(Activity activity) {

        try {
            //Check if there is write permission
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // No permission to write, to apply for permission to write, a dialog box will pop up
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_external_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ButterKnife.bind(this);

        verifyStoragePermissions(this);
        //Copy model to sk card
        try {
            copyBigDataToSD("det1.bin");
            copyBigDataToSD("det2.bin");
            copyBigDataToSD("det3.bin");
            copyBigDataToSD("det1.param");
            copyBigDataToSD("det2.param");
            copyBigDataToSD("det3.param");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Model initialization
        init();

        mCameraHelper.setOnPreviewFrameListener(this);

//
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

    }

    @Override
    public void onBackPressed() {
        finish();
    }
    @Override
    public void onStart() {
        super.onStart();
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }
    @Override
    public void onStop() {
        super.onStop();
        try {
            if (mCameraHelper != null) {
                mCameraHelper.unregisterUSB();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mCameraHelper != null) {
                mCameraHelper.release();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void onAttachDev(UsbDevice device) {
        Log.i("test_attached","Attached error");
        if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
            displayToast("No USB Camera Attached");
            return;
        }
        if (!isRequest) {
            isRequest = true;
            if (mCameraHelper != null)
                mCameraHelper.requestPermission(0);
        }
    }
    @Override
    public void onDettachDev(UsbDevice device) {
        Log.i("test_dettached","Dettached error");
        try {
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                displayToast(device.getDeviceName() + " is detached");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void onConnectDev(UsbDevice device, boolean isConnected) {
        Log.i("test_connect","Connection error");
        if (!isConnected) {
            displayToast("Failed to connect. Please check resolution params");
            isPreview = false;
        } else {
            isPreview = true;
            displayToast("Connecting...");
            new Thread(() -> {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    @Override
    public void onDisConnectDev(UsbDevice device) {
//        viewModel.stopCamera();
        Log.i("test_disconnect","Disconnection error");
        displayToast("Disconnecting...");
    }
    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }
    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled)
            displayToast("Camera disconnected unexpectedly");
    }
    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        try {
            if (!isPreview && mCameraHelper.isCameraOpened()) {
                mCameraHelper.startPreview(mUVCCameraView);
                isPreview = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {
    }
    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        try {
            if (isPreview && mCameraHelper.isCameraOpened()) {
                mCameraHelper.stopPreview();
                isPreview = false;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void log(String s) {
//        Log.i("testing",s);
    }

    @Override
    public void onPreviewResult(byte[] bytes) {
        if (isProcessingFrame) {
            log("Dropping frame!");
            return;
        }
        isProcessingFrame = true;

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                previewHeight = mCameraHelper.getPreviewHeight();
                previewWidth = mCameraHelper.getPreviewWidth();
                rgbBytes = new int[previewWidth * previewHeight];
//                if(LegacyCameraConnectionFragment.camera_id == Camera.CameraInfo.CAMERA_FACING_BACK) //wjy 前后摄像头旋转角度不同
                onPreviewSizeChosen(new Size(previewWidth, previewHeight), 0);
//                else if(LegacyCameraConnectionFragment.camera_id == Camera.CameraInfo.CAMERA_FACING_FRONT)
//                    onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 270);
            }
        } catch (final Exception e) {
            e.printStackTrace();
            log(e.getMessage());
            return;
        }

        log("Start onPreviewResult 1");

        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter =
                () -> ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
        log("Start onPreviewResult 2");

        postInferenceCallback =
                () -> isProcessingFrame = false;
        log("Start onPreviewResult 3");
        processImage();
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        log("Start processImage 1");

//        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                i.setImageBitmap(rgbFrameBitmap);
//            }
//        });
        tracker.onFrame(
                rgbFrameBitmap,
                previewWidth,
                previewHeight,
                getLuminanceStride(),
                0,
                originalLuminance,
                timestamp);
        trackingOverlay.postInvalidate();  //界面刷新,postInvalidate 在非UI线程中使用
        log("Start processImage 2");

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            log("Start processImage 2.1");
            readyForNextImage();
            return;
        }
        computingDetection = true;
        log("Start processImage 3");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        //ImageUtils.saveBitmap(rgbFrameBitmap,"wjy.jpg");

        log("Start processImage 4");


        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        readyForNextImage();


        final Canvas canvas = new Canvas(croppedBitmap);  //croppedBitmap创建以后，通过canvas.drawBitmap获取变换后的bitmap
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        try {
            runInBackground(
                    new Runnable() {     // 匿名类
                        @Override
                        public void run() {

                            log("Start processImage 5");
                            final long startTime = SystemClock.uptimeMillis();
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    i.setImageBitmap(croppedBitmap);
//                                }
//                            });
                            List<Classifier.Recognition> mappedRecognitions =
                                    classifier.recognizeImage(croppedBitmap, cropToFrameTransform);

                            log("Start processImage 7");

                            try {
                                tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            trackingOverlay.postInvalidate();
                            computingDetection = false;

                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
        log("Start processImage 6");
    }

    private void copyBigDataToSD(String strOutFileName) throws IOException {
        Log.i(TAG, "start copy file " + strOutFileName);
        File sdDir = Environment.getExternalStorageDirectory();
        File file = new File(sdDir.toString() + "/mtcnn1/");
        if (!file.exists()) {
            file.mkdir();
        }

        String tmpFile = sdDir.toString() + "/mtcnn1/" + strOutFileName;
        File f = new File(tmpFile);

        if (f.exists()) {
            Log.i(TAG, "file exists " + strOutFileName);
            return;
        }

        InputStream myInput;
        java.io.OutputStream myOutput = new FileOutputStream(sdDir.toString() + "/mtcnn1/" + strOutFileName);
        myInput = this.getAssets().open(strOutFileName);
        byte[] buffer = new byte[1024];
        int length = myInput.read(buffer);
        while (length > 0) {
            myOutput.write(buffer, 0, length);
            length = myInput.read(buffer);
        }
        myOutput.flush();
        myInput.close();
        myOutput.close();
        Log.i(TAG, "end copy file " + strOutFileName);

    }
    private void init(){

        classifier = new Classifier();
        mUVCCameraView = (CameraViewInterface) textureViewUsb;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, this);
        mCameraHelper.updateResolution(1280,720 );
        if (mCameraHelper != null && mCameraHelper.isCameraOpened())
            mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 100);

    }
    public void displayToast(String message){
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }


    public void onPreviewSizeChosen(final Size size, final int rotation) {
        log("Start onPreviewSizeChosen");
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        log("Start onPreviewSizeChosen 1");

        tracker = new MultiBoxTracker(this);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        log("Start onPreviewSizeChosen 2");

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);

        log("Start onPreviewSizeChosen 3");

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        1280, 720,
                        0, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        log("Start onPreviewSizeChosen 4");

        i = findViewById(R.id.i);

        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> {
                    log("Start onPreviewSizeChosen 5");
                    tracker.draw(canvas);
                });

        log("Start onPreviewSizeChosen 6");
    }

}
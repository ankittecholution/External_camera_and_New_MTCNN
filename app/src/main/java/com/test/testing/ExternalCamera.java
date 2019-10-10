package com.test.testing;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.YuvImage;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.test.faceopen.R;
import com.test.testing.env.BorderedText;
import com.test.testing.env.ImageUtils;
import com.test.testing.tracking.MultiBoxTracker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ExternalCamera extends AppCompatActivity implements UVCCameraHelper.OnMyDevConnectListener, CameraDialog.CameraDialogParent, CameraViewInterface.Callback, AbstractUVCCameraHandler.OnPreViewResultListener {


    private static final int CROP_SIZE = 300;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(1280, 720);

    private static final float TEXT_SIZE_DIP = 10;
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    public ImageView i;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    protected TextView frameValueTextView, cropValueTextView, inferenceTimeTextView;
    protected ImageView bottomSheetArrowImageView;
    OverlayView trackingOverlay;
    private Classifier classifier;
    private boolean debug = false;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private LinearLayout bottomSheetLayout;
    private LinearLayout gestureLayout;
    private BottomSheetBehavior sheetBehavior;
    private ImageView plusImageView, minusImageView;
    private SwitchCompat apiSwitchCompat;
    private TextView threadsTextView;
    private Integer sensorOrientation;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private byte[] luminanceCopy;
    private BorderedText borderedText;
    private Snackbar initSnackbar;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    @BindView(R.id.externalCamera)
    public View textureViewUsb;
    private boolean initialized = false;

    String TAG = "External_cam_testing";

    boolean runnimg = true;

    YuvImage yuv;
    ByteArrayOutputStream out;
    int width;
    int height;
    private boolean isRequest;
    private boolean isPreview;
    byte[] bytes1;
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
//    private MTCNN newmtcnn = new MTCNN();



//    @Override
//    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
//        super.onSaveInstanceState(outState, outPersistentState);
//        isOnSavedInstanceCalled = true;
//    }

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
        File sdDir = Environment.getExternalStorageDirectory();//Get the directory
        String sdPath = sdDir.toString() + "/mtcnn1/";
//        newmtcnn.FaceDetectionModelInit(sdPath);
//
//        newmtcnn.SetMinFaceSize(150);
//        newmtcnn.SetThreadsNumber(1);
//        newmtcnn.SetTimeCount(1);

        mCameraHelper.setOnPreviewFrameListener(this);

//
//        if (isExternalCam)
//        else
//            switchFragment(externalCamFragment,internalCameraFragment,INTERNAL_CAM_FRAG);
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
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };
        log("Start onPreviewResult 2");

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        isProcessingFrame = false;
                    }
                };
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
        Log.i("testing", "runInBackground 1");
        if (handler != null) {
            Log.i("testing", "runInBackground 2");
            handler.post(r);
            Log.i("testing", "runInBackground 3");
        }
    }

    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        log("Start processImage 1");
        tracker.onFrame(
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
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    i.setImageBitmap(croppedBitmap);
                                }
                            });
                            List<Classifier.Recognition> mappedRecognitions =
                                    classifier.recognizeImage(croppedBitmap, cropToFrameTransform);

                            log("Start processImage 7");

                            tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
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
        try {
            classifier = Classifier.getInstance(getAssets());
        } catch (Exception e) {
            finish();
        }
        mUVCCameraView = (CameraViewInterface) textureViewUsb;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, this);
        mCameraHelper.updateResolution(1280,720 );
        if (mCameraHelper != null && mCameraHelper.isCameraOpened())
            mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 70);

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


//    private class FaceDetection extends AsyncTask<byte[], Integer, JSONObject> {
//
////        MTCNN mtcnn;
//        JSONObject face;
//        int[] faceInfo;
//        Bitmap bitmap;
//        JSONArray recIDK;
//
//        int width, height;
//
//        FaceDetection(MTCNN mtcnn, int width, int height) {
//            this.mtcnn = mtcnn;
//            this.width = width;
//            this.height = height;
//        }
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            runnimg = false;
//
//        }
//
//        private byte[] getPixelsRGBA(Bitmap image) {
//            // calculate how many bytes our image consists of
//            int bytes = image.getByteCount();
//            ByteBuffer buffer = ByteBuffer.allocate(bytes); // Create a new buffer
//            image.copyPixelsToBuffer(buffer); // Move the byte data to the buffer
//            byte[] temp = buffer.array(); // Get the underlying array containing the
//            return temp;
//        }
//
//
//
//        @Override
//        protected JSONObject doInBackground(byte[]... bitmaps) {
//            JSONObject done = new JSONObject();
//            face = new JSONObject();
//            JSONArray landIDK = new JSONArray();
//            recIDK = new JSONArray();
//            JSONArray poseIDK = new JSONArray();
//            bitmap = BitmapFactory.decodeByteArray(bitmaps[0], 0, bitmaps[0].length);
//            byte[] b = getPixelsRGBA(bitmap);
//            try {
//                long ti = System.currentTimeMillis();
//                faceInfo = mtcnn.FaceDetect(b, width, height, 4);
//                Log.i(TAG, (System.currentTimeMillis() - ti) + " " + faceInfo[0]);
//                if (faceInfo[0]>0) {
//                    for (int i = 0; i < faceInfo[0]; i++) {
//                        JSONArray recArr = new JSONArray();
//                        recArr.put(faceInfo[1 + 14 * i]);
//                        recArr.put(faceInfo[2 + 14 * i]);
//                        recArr.put(faceInfo[3 + 14 * i] - faceInfo[1 + 14 * i] + 1);
//                        recArr.put(faceInfo[4 + 14 * i] - faceInfo[2 + 14 * i] + 1);
//                        recIDK.put(recArr);
//                        JSONArray landArray = new JSONArray();
//                        landArray.put(faceInfo[5 + 14 * i]);
//                        landArray.put(faceInfo[6 + 14 * i]);
//                        landArray.put(faceInfo[7 + 14 * i]);
//                        landArray.put(faceInfo[8 + 14 * i]);
//                        landArray.put(faceInfo[9 + 14 * i]);
//                        landArray.put(faceInfo[10 + 14 * i]);
//                        landArray.put(faceInfo[11 + 14 * i]);
//                        landArray.put(faceInfo[12 + 14 * i]);
//                        landArray.put(faceInfo[13 + 14 * i]);
//                        landArray.put(faceInfo[14 + 14 * i]);
//                        landIDK.put(landArray);
//                        String s = getPose(i);
//                        Log.i("Pose", getPose(i));
//                        poseIDK.put(s);
//                    }
//                    face.put("rects", recIDK);
//                    face.put("landmarks", landIDK);
//                    face.put("face_pose", poseIDK);
//                    done.put("faces", face.toString());
//                }
//            } catch (Exception e) {
//                Log.e("Error Face Detection", "[*]detect false:" + e);
//            }
//            return done;
//        }
//
//
//
//        @Override
//        protected void onPostExecute(JSONObject done) {
//            if (faceInfo[0]>0)
//                Log.i(TAG,done.toString());
//            runnimg = true;
//        }
//
//        private String getPose(int i) {
//            int right = abs(faceInfo[6 + 14 * i] - faceInfo[7 + 14 * i]), left = abs(faceInfo[5 + 14 * i] - faceInfo[7 + 14 * i]);
//            if (right==0)
//                right = 1;
//            if (left==0)
//                left = 1;
//
//            if (abs(faceInfo[5 + 14 * i] - faceInfo[7 + 14 * i]) / right > 4)
//                return "left";
//            else if (abs(faceInfo[6 + 14 * i] - faceInfo[7 + 14 * i]) / left > 4)
//                return "right";
//            else
//                return "center";
//        }
//    }

}
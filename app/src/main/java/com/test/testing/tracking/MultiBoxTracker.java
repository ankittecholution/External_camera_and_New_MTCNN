
package com.test.testing.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.widget.Toast;

import com.test.testing.ApiClient;
import com.test.testing.Apis;
import com.test.testing.Classifier;
import com.test.testing.Classifier.Recognition;
import com.test.testing.env.BorderedText;
import com.test.testing.env.ImageUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A tracker wrapping ObjectTracker that also handles non-max suppression and matching existing
 * objects to new detections.
 */
public class MultiBoxTracker {

    // Allow replacement of the tracked box with new results if
    // correlation has dropped below this level.
    private static final float MARGINAL_CORRELATION = 0.8f;
    // Consider object to be lost if correlation falls below this threshold.
    private static final float MIN_CORRELATION = 0.3f;
    private static final float TEXT_SIZE_DIP = 10;

    // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
    // the lower scored box (new or old) will be removed.
    private static final float MAX_OVERLAP = 0.2f;

    private static final float MIN_SIZE = 16.0f;
    Bitmap b = null;
    HashMap<Integer, Boolean> id = new HashMap<>();

    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };

    private final Queue<Integer> availableColors = new LinkedList<Integer>();
    private final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
    private final Paint boxPaint = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    private ObjectTracker objectTracker;
    private Matrix frameToCanvasMatrix;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;
    private Context context;
    private boolean initialized = false;

    public MultiBoxTracker(final Context context) {
        this.context = context;
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(5.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    JSONArray landIDK;
    JSONArray recIDK;
    JSONArray poseIDK;
    JSONArray color;

    public synchronized void trackResults(
            final List<Classifier.Recognition> results, final byte[] frame, final long timestamp) throws JSONException {
        processResults(timestamp, results, frame);
    }

    public synchronized void draw(final Canvas canvas) {

//        Log.i("rectTesting_draw3","qwedwasasdd");

        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos =
                    (objectTracker != null)
                            ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
                            : new RectF(recognition.location);
            try {
//                Log.i("rectTesting_draw", recognition.color+"   "+trackedPos.toString()+"   "+System.currentTimeMillis());
            } catch (Exception e) {
//                Log.i("rectTesting_draw2", e.toString());
            }

            getFrameToCanvasMatrix().mapRect(trackedPos);
            boxPaint.setColor(recognition.color);

            final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, "");
        }
    }

    public synchronized void onFrame(
            Bitmap b,
            final int w,
            final int h,
            final int rowStride,
            final int sensorOrienation,
            final byte[] frame,
            final long timestamp) {


        this.b = b;
        Log.i("rectTesting_process", "Start-----------------------------------------------------------------------------------------");
        if (objectTracker == null && !initialized) {
            ObjectTracker.clearInstance();
            objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
            frameWidth = w;
            frameHeight = h;
            this.sensorOrientation = sensorOrienation;
            initialized = true;

            if (objectTracker == null) {
                String message =
                        "Object tracking support not found. "
                                + "See tensorflow/examples/android/README.md for details.";
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        }

        if (objectTracker == null) {
            return;
        }

        objectTracker.nextFrame(frame, null, timestamp, null, true);

        // Clean up any objects not worth tracking any more.
        final LinkedList<TrackedRecognition> copyList =
                new LinkedList<TrackedRecognition>(trackedObjects);
        for (final TrackedRecognition recognition : copyList) {
            final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
            final float correlation = trackedObject.getCurrentCorrelation();
            Log.i("testingcolrrelation", correlation + "    " + recognition.time + "    " + recognition.color);
            if (correlation < MIN_CORRELATION) {
                trackedObject.stopTracking();
                trackedObjects.remove(recognition);
                availableColors.add(recognition.color);
                id.remove(recognition.color);
                Log.i("testingcolrrelation", correlation + "  Stopped    " + recognition.time + "    " + recognition.color);
            }
        }
    }

    private void processResults(
            final long timestamp, final List<Recognition> results, final byte[] originalFrame) throws JSONException {
        final List<Pair<Float, Classifier.Recognition>> rectsToTrack = new LinkedList<Pair<Float, Classifier.Recognition>>();

        JSONObject face = new JSONObject();
        JSONObject done = new JSONObject();
        landIDK = new JSONArray();
        recIDK = new JSONArray();
        poseIDK = new JSONArray();
        color = new JSONArray();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

//            Log.i("rectTesting_process",result.getLocation().toString()+"    "+result.getLandmark());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            screenRects.add(new Pair<>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                continue;
            }

            rectsToTrack.add(new Pair<>(result.getConfidence(), result));
        }

        if (rectsToTrack.isEmpty()) {
            return;
        }

        if (objectTracker == null) {
            trackedObjects.clear();
            for (final Pair<Float, Recognition> potential : rectsToTrack) {
                final TrackedRecognition trackedRecognition = new TrackedRecognition();
                trackedRecognition.detectionConfidence = potential.first;
                trackedRecognition.location = new RectF(potential.second.getLocation());
                trackedRecognition.trackedObject = null;
                trackedRecognition.title = potential.second.getTitle();
                trackedRecognition.color = COLORS[trackedObjects.size()];
                trackedRecognition.id = System.currentTimeMillis();
                trackedObjects.add(trackedRecognition);
                if (trackedObjects.size() >= COLORS.length) {
                    break;
                }
            }
            return;
        }
        for (final Pair<Float, Recognition> potential : rectsToTrack) {
//            Log.i("rectTesting_inside","indise for");
            handleDetection(originalFrame, timestamp, potential);
        }
        face.put("rects", recIDK);
        face.put("landmarks", landIDK);
        face.put("face_pose", poseIDK);
        done.put("faces", face.toString());
        if (recIDK.length() > 0)
            apiCall(face, color);
        Log.i("rectTesting_process", "Done-----------------------------------------------------------------------------------------");
    }

    private void handleDetection(final byte[] frameCopy, final long timestamp, final Pair<Float, Recognition> potential) {
        final ObjectTracker.TrackedObject potentialObject =
                objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy);

        final float potentialCorrelation = potentialObject.getCurrentCorrelation();


        if (potentialCorrelation < MARGINAL_CORRELATION) {
            potentialObject.stopTracking();
//            Log.i("testingcMARGINAL", potentialCorrelation + "  " + "Stopped");
            return;
        }

        final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

        float maxIntersect = 0.0f;

        // This is the current tracked object whose color we will take. If left null we'll take the
        // first one from the color queue.
        TrackedRecognition recogToReplace = null;

        // Look for intersections that will be overridden by this object or an intersection that would
        // prevent this one from being placed.
        for (final TrackedRecognition trackedRecognition : trackedObjects) {
            final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
            final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
            final RectF intersection = new RectF();
            final boolean intersects = intersection.setIntersect(a, b);

            final float intersectArea = intersection.width() * intersection.height();
            final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
            final float intersectOverUnion = intersectArea / totalArea;

            // If there is an intersection with this currently tracked box above the maximum overlap
            // percentage allowed, either the new recognition needs to be dismissed or the old
            // recognition needs to be removed and possibly replaced with the new one.
            if (intersects && intersectOverUnion > MAX_OVERLAP) {
                if (potential.first < trackedRecognition.detectionConfidence
                        && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
                    // If track for the existing object is still going strong and the detection score was
                    // good, reject this new object.
                    potentialObject.stopTracking();
                    return;
                } else {
                    removeList.add(trackedRecognition);

                    // Let the previously tracked object with max intersection amount donate its color to
                    // the new object.
                    if (intersectOverUnion > maxIntersect) {
                        maxIntersect = intersectOverUnion;
                        recogToReplace = trackedRecognition;
                    }
                }
            }
        }

        // If we're already tracking the max object and no intersections were found to bump off,
        // pick the worst current tracked object to remove, if it's also worse than this candidate
        // object.
        if (availableColors.isEmpty() && removeList.isEmpty()) {
            for (final TrackedRecognition candidate : trackedObjects) {
                if (candidate.detectionConfidence < potential.first) {
                    if (recogToReplace == null
                            || candidate.detectionConfidence < recogToReplace.detectionConfidence) {
                        // Save it so that we use this color for the new object.
                        recogToReplace = candidate;
                    }
                }
            }
            if (recogToReplace != null) {
                removeList.add(recogToReplace);
            }
        }

        // Remove everything that got intersected.
        for (final TrackedRecognition trackedRecognition : removeList) {
            trackedRecognition.trackedObject.stopTracking();
            trackedObjects.remove(trackedRecognition);
            if (trackedRecognition != recogToReplace) {
                Log.i("rectTesting_color", trackedRecognition.color + "");
                availableColors.add(trackedRecognition.color);
            }
        }

        if (recogToReplace == null && availableColors.isEmpty()) {
            potentialObject.stopTracking();
            return;
        }

        // Finally safe to say we can track this object.
        final TrackedRecognition trackedRecognition = new TrackedRecognition();
        trackedRecognition.detectionConfidence = potential.first;
        trackedRecognition.trackedObject = potentialObject;
        trackedRecognition.title = potential.second.getTitle();
        trackedRecognition.id = System.currentTimeMillis();

        // Use the color from a replaced object before taking one from the color queue.
        trackedRecognition.color =
                recogToReplace != null ? recogToReplace.color : availableColors.poll();
        trackedObjects.add(trackedRecognition);

        if (!id.containsKey(trackedRecognition.color))
            id.put(trackedRecognition.color, true);
        if (id.get(trackedRecognition.color)) {
            Log.i("rectTesting_handle", trackedRecognition.id + " " + trackedRecognition.color + " location:- " + potential.second.getLandmark());
            recIDK.put(potential.second.getRecArr());
            landIDK.put(potential.second.getLandArray());
            poseIDK.put("center");
        }
        color.put(trackedRecognition.color);
    }


    private File createFile(Bitmap bm) {
        File f = new File(context.getCacheDir(), System.currentTimeMillis() + "");
        try {
            f.createNewFile();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            byte[] bitmapData = bos.toByteArray();

            //write the bytes in file
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bitmapData);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    public void apiCall(JSONObject done, JSONArray color) {
        long farmeId = System.currentTimeMillis();
        Log.i("rectTesting_apicall", "start Api   " + farmeId + " " + done.toString());
        Log.i("rectTesting_apicall", id.toString() + color.toString());
        File file = createFile(b);
        Apis api = ApiClient.getClient().create(Apis.class);
        RequestBody reqFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), reqFile);
        final long startTime = System.currentTimeMillis();
        try {
            api.sendImage(body, "5d81e6d349ff94000fe7e3dd", done.toString(),
                    farmeId + "").enqueue(new Callback<String>() {
                @Override
                public void onResponse(Call<String> call, Response<String> respons) {
                    if (respons.code() == 200) {
                        Log.i("rectTesting_total_time", startTime - System.currentTimeMillis() + " " + respons.body());
                        try {
                            JSONObject response = new JSONObject(respons.body());
                            if (response.getString("status").contains("200")) {
                                Log.i("rectTesting_response_va", response.getJSONArray("log").toString());
                                if (response.getJSONArray("log").getJSONObject(0).getBoolean("display")) {
                                    Log.i("rectTesting_apicall", color.toString());
                                    id.remove(color.getInt(0));
                                    id.put(color.getInt(0), false);
                                }
                            } else {
                                Log.i("rectTesting_check_err", response.toString());
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e("rectTesting_check_err", "code not 200" + respons.toString() + "   " + done.toString());
                    }
                }

                @Override
                public void onFailure(Call<String> call, Throwable t) {
                    Log.e("rectTesting_check_err", t.toString());
                    if (!t.toString().contains("FileNotFoundException")) {
                        Log.i("rectTesting_check_err", "inside FileNotFoundException");
                    } else {
                        Log.i("rectTesting_check_err", "Solved");
                    }
                    t.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TrackedRecognition {
        ObjectTracker.TrackedObject trackedObject;
        RectF location;
        float detectionConfidence;
        int color;
        String title;
        long time;
        long id;
    }
}

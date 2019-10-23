/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.test.testing;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Environment;

import androidx.core.util.Pair;

import org.json.JSONArray;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Generic interface for interacting with different recognition engines.
 */
public class Classifier {

    int[] faceInfo;
    private MTCNN newmtcnn = new MTCNN();

    public Classifier() {
        File sdDir = Environment.getExternalStorageDirectory();//Get the directory
        String sdPath = sdDir.toString() + "/mtcnn1/";
        newmtcnn.FaceDetectionModelInit(sdPath);
        newmtcnn.SetMinFaceSize(150);
        newmtcnn.SetThreadsNumber(1);
        newmtcnn.SetTimeCount(1);
    }

    private byte[] getPixelsRGBA(Bitmap image) {
        // calculate how many bytes our image consists of
        int bytes = image.getByteCount();
        ByteBuffer buffer = ByteBuffer.allocate(bytes); // Create a new buffer
        image.copyPixelsToBuffer(buffer); // Move the byte data to the buffer
        byte[] temp = buffer.array(); // Get the underlying array containing the
        return temp;
    }

    List<Recognition> recognizeImage(Bitmap bitmap, Matrix matrix) {
        synchronized (this) {
            JSONArray landIDK = new JSONArray();
            JSONArray recIDK = new JSONArray();
            JSONArray poseIDK = new JSONArray();
            long time = System.currentTimeMillis();

            byte[] b = getPixelsRGBA(bitmap);
            faceInfo = newmtcnn.FaceDetect(b, bitmap.getWidth(), bitmap.getHeight(), 4);
            Pair[] faces = new Pair[faceInfo[0]];

            JSONArray landmark = new JSONArray();

            final List<Recognition> mappedRecognitions = new LinkedList<>();


            for (int i = 0; i < faceInfo[0]; i++) {
                faces[i] = new Pair<>(
                        new RectF(faceInfo[1 + 14 * i], faceInfo[2 + 14 * i], faceInfo[3 + 14 * i], faceInfo[4 + 14 * i]), 12.5f);
                JSONArray recArr = new JSONArray();
                recArr.put(faceInfo[1 + 14 * i]);
                recArr.put(faceInfo[2 + 14 * i]);
                recArr.put(faceInfo[3 + 14 * i] - faceInfo[1 + 14 * i] + 1);
                recArr.put(faceInfo[4 + 14 * i] - faceInfo[2 + 14 * i] + 1);
                recIDK.put(recArr);
                JSONArray landArray = new JSONArray();
                landArray.put(faceInfo[5 + 14 * i]);
                landArray.put(faceInfo[6 + 14 * i]);
                landArray.put(faceInfo[7 + 14 * i]);
                landArray.put(faceInfo[8 + 14 * i]);
                landArray.put(faceInfo[9 + 14 * i]);
                landArray.put(faceInfo[10 + 14 * i]);
                landArray.put(faceInfo[11 + 14 * i]);
                landArray.put(faceInfo[12 + 14 * i]);
                landArray.put(faceInfo[13 + 14 * i]);
                landArray.put(faceInfo[14 + 14 * i]);
//                Log.i("rectTesting_recognize", "" + (faceInfo[1 + 14 * i]+ " "+faceInfo[2 + 14 * i]+ "  " +faceInfo[3 + 14 * i]+" " + faceInfo[4 + 14 * i])+"   tiime "+System.currentTimeMillis());
                Pair face = faces[i];
                RectF rectF = (RectF) face.first;

                matrix.mapRect(rectF);
                Float prob = (Float) face.second;
                String name;
                name = "";

                Recognition result =
                        new Recognition("" + face.second, name, prob, recArr, landArray, rectF, face.toString());
                mappedRecognitions.add(result);
            }
            return mappedRecognitions;
        }

    }


    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    public class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /**
         * Optional location within the source image for the location of the recognized object.
         */
        private RectF location;

        JSONArray recArr;

        JSONArray landArray;

        private String json;

        Recognition(
                final String id, final String title, final Float confidence, final JSONArray recArr, final JSONArray landArr, final RectF location, final String json) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.recArr = recArr;
            this.landArray = landArr;
            this.json = json;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return location;
        }

        public JSONArray getRecArr() {
            return recArr;
        }

        public JSONArray getLandArray() {
            return landArray;
        }

        public String getLandmark() {
            return json;
        }


        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }
}

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

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;

import androidx.core.util.Pair;

import com.test.testing.wrapper.MTCNN1;

import java.util.LinkedList;
import java.util.List;

/**
 * Generic interface for interacting with different recognition engines.
 */
public class Classifier {
    private static Classifier classifier;
    private MTCNN1 mtcnn;

    private Classifier() {
    }

    static Classifier getInstance(AssetManager assetManager) {
        if (classifier != null) return classifier;

        classifier = new Classifier();

        classifier.mtcnn = MTCNN1.create(assetManager);
        return classifier;
    }

    List<Recognition> recognizeImage(Bitmap bitmap, Matrix matrix) {
        synchronized (this) {
            long time = System.currentTimeMillis();
            Pair[] faces = mtcnn.detect(bitmap);
            Log.i("mtcnn time", "" + (System.currentTimeMillis() - time) + "  " + faces.length);

            final List<Recognition> mappedRecognitions = new LinkedList<>();

            for (Pair face : faces) {
                RectF rectF = (RectF) face.first;

//                Rect rect = new Rect();
//                rectF.round(rect);

                matrix.mapRect(rectF);
                Float prob = (Float) face.second;
                String name;
                name = "Unknown";

                Recognition result =
                        new Recognition("" + face.second, name, prob, rectF);
                mappedRecognitions.add(result);
            }
            return mappedRecognitions;
        }

    }

    void close() {
        mtcnn.close();
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

        Recognition(
                final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
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
            return new RectF(location);
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

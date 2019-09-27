package com.test.testing.env;

import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;

public class FileUtils {
    public static final String ROOT =
            Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "facerecognizer";
    public static final String DATA_FILE = "data";
    public static final String MODEL_FILE = "model";
    public static final String LABEL_FILE = "label";
    private static final Logger LOGGER = new Logger();

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap   The bitmap to save.
     * @param filename The location to save the bitmap to.
     */
    public static void saveBitmap(final Bitmap bitmap, final String filename) {
        LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), ROOT);
        final File myDir = new File(ROOT);

        if (!myDir.mkdirs()) {
            LOGGER.i("Make dir failed");
        }

        final File file = new File(myDir, filename);
        if (file.exists()) {
            file.delete();
        }
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
        }
    }


}

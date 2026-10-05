package net.sourceforge.opencamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Static methods for handling images.
 */
public class ImageUtils {
    private static final String TAG = "ImageUtils";

    private static void setBitmapOptionsSampleSize(BitmapFactory.Options options, int inSampleSize) {
        if( MyDebug.LOG )
            Log.d(TAG, "setBitmapOptionsSampleSize: " + inSampleSize);
        //options.inSampleSize = inSampleSize;
        if( inSampleSize > 1 ) {
            // use inDensity for better quality, as inSampleSize uses nearest neighbour
            options.inDensity = inSampleSize;
            options.inTargetDensity = 1;
        }
    }

    /** Loads a single jpeg as a Bitmaps.
     * @param mutable Whether the bitmap should be mutable. Note that when converting to bitmaps
     *                for the image post-processing (auto-stabilise etc), in general we need the
     *                bitmap to be mutable (for photostamp to work).
     */
    static Bitmap loadBitmap(byte [] jpeg_image, boolean mutable, int inSampleSize) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "loadBitmap");
            Log.d(TAG, "mutable?: " + mutable);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        if( MyDebug.LOG )
            Log.d(TAG, "options.inMutable is: " + options.inMutable);
        options.inMutable = mutable;
        setBitmapOptionsSampleSize(options, inSampleSize);
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg_image, 0, jpeg_image.length, options);
        if( bitmap == null ) {
            Log.e(TAG, "failed to decode bitmap");
        }
        return bitmap;
    }

    /** Helper class for loadBitmaps().
     */
    private static class LoadBitmapThread extends Thread {
        Bitmap bitmap;
        final BitmapFactory.Options options;
        final byte [] jpeg;
        LoadBitmapThread(BitmapFactory.Options options, byte [] jpeg) {
            super("LoadBitmapThread");
            this.options = options;
            this.jpeg = jpeg;
        }

        public void run() {
            this.bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        }
    }

    /** Converts the array of jpegs to Bitmaps. The bitmap with index mutable_id will be marked as mutable (or set to -1 to have no mutable bitmaps, or -2 to have all be mutable bitmaps).
     */
    static List<Bitmap> loadBitmaps(List<byte []> jpeg_images, int mutable_id, int inSampleSize) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "loadBitmaps");
            Log.d(TAG, "mutable_id: " + mutable_id);
        }
        BitmapFactory.Options mutable_options = new BitmapFactory.Options();
        mutable_options.inMutable = true; // bitmap that needs to be writable
        setBitmapOptionsSampleSize(mutable_options, inSampleSize);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = false; // later bitmaps don't need to be writable
        setBitmapOptionsSampleSize(options, inSampleSize);
        LoadBitmapThread [] threads = new LoadBitmapThread[jpeg_images.size()];
        for(int i=0;i<jpeg_images.size();i++) {
            threads[i] = new LoadBitmapThread( (i==mutable_id || mutable_id==-2) ? mutable_options : options, jpeg_images.get(i) );
        }
        // start threads
        if( MyDebug.LOG )
            Log.d(TAG, "start threads");
        for(int i=0;i<jpeg_images.size();i++) {
            threads[i].start();
        }
        // wait for threads to complete
        boolean ok = true;
        if( MyDebug.LOG )
            Log.d(TAG, "wait for threads to complete");
        try {
            for(int i=0;i<jpeg_images.size();i++) {
                threads[i].join();
            }
        }
        catch(InterruptedException e) {
            MyDebug.logStackTrace(TAG, "threads interrupted", e);
            ok = false;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "threads completed");

        List<Bitmap> bitmaps = new ArrayList<>();
        for(int i=0;i<jpeg_images.size() && ok;i++) {
            Bitmap bitmap = threads[i].bitmap;
            if( bitmap == null ) {
                Log.e(TAG, "failed to decode bitmap in thread: " + i);
                ok = false;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "bitmap " + i + ": " + bitmap + " is mutable? " + bitmap.isMutable());
            }
            bitmaps.add(bitmap);
        }

        if( !ok ) {
            if( MyDebug.LOG )
                Log.d(TAG, "cleanup from failure");
            for(int i=0;i<jpeg_images.size();i++) {
                if( threads[i].bitmap != null ) {
                    threads[i].bitmap.recycle();
                    threads[i].bitmap = null;
                }
            }
            bitmaps.clear();
            System.gc();
            return null;
        }

        return bitmaps;
    }

    /** Loads the bitmap from the supplied jpeg data, rotating if necessary according to the
     *  supplied EXIF orientation tag.
     * @param data The jpeg data.
     * @param mutable Whether to create a mutable bitmap.
     * @return A bitmap representing the correctly rotated jpeg.
     */
    static Bitmap loadBitmapWithRotation(byte [] data, boolean mutable) {
        Bitmap bitmap = loadBitmap(data, mutable, 1);
        if( bitmap != null ) {
            // rotate the bitmap if necessary for exif tags
            if( MyDebug.LOG )
                Log.d(TAG, "rotate bitmap for exif tags?");
            bitmap = rotateForExif(bitmap, data);
        }
        return bitmap;
    }

    /** Rotates the supplied bitmap according to the orientation tag stored in the exif data. If no
     *  rotation is required, the input bitmap is returned. If rotation is required, the input
     *  bitmap is recycled.
     * @param exif The Exif information to use.
     */
    private static Bitmap rotateForExif(Bitmap bitmap, ExifInterface exif) {
        int exif_orientation_s = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        if( MyDebug.LOG )
            Log.d(TAG, "    exif orientation string: " + exif_orientation_s);
        boolean needs_tf = false;
        int exif_orientation = 0;
        // see http://jpegclub.org/exif_orientation.html
        // and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
        switch( exif_orientation_s ) {
            case ExifInterface.ORIENTATION_UNDEFINED:
            case ExifInterface.ORIENTATION_NORMAL:
                // leave unchanged
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                needs_tf = true;
                exif_orientation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                needs_tf = true;
                exif_orientation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                needs_tf = true;
                exif_orientation = 270;
                break;
            default:
                // just leave unchanged for now
                if( MyDebug.LOG )
                    Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
                break;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "    exif orientation: " + exif_orientation);

        if( needs_tf ) {
            if( MyDebug.LOG )
                Log.d(TAG, "    need to rotate bitmap due to exif orientation tag");
            Matrix m = new Matrix();
            m.setRotate(exif_orientation, bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f);
            Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), m, true);
            if( rotated_bitmap != bitmap ) {
                bitmap.recycle();
                bitmap = rotated_bitmap;
            }
        }
        return bitmap;
    }

    /** Rotates the supplied bitmap according to the orientation tag stored in the exif data. If no
     *  rotation is required, the input bitmap is returned. If rotation is required, the input
     *  bitmap is recycled.
     * @param data Jpeg data containing the Exif information to use.
     */
    static Bitmap rotateForExif(Bitmap bitmap, byte [] data) {
        if( MyDebug.LOG )
            Log.d(TAG, "rotateForExif");
        if( bitmap == null ) {
            // support thumbnail being null - as this can happen according to Google Play crashes, see comment in saveSingleImageNow()
            return null;
        }
        InputStream inputStream = null;
        try {
            ExifInterface exif;

            if( MyDebug.LOG )
                Log.d(TAG, "use data stream to read exif tags");
            inputStream = new ByteArrayInputStream(data);
            exif = new ExifInterface(inputStream);
            bitmap = rotateForExif(bitmap, exif);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "exif orientation ioexception", e);
        }
        catch(NoClassDefFoundError e) {
            // have had Google Play crashes from new ExifInterface() for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn)
            MyDebug.logStackTrace(TAG, "exif orientation NoClassDefFoundError", e);
        }
        finally {
            if( inputStream != null ) {
                try {
                    inputStream.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to close inputStream", e);
                }
            }
        }
        return bitmap;
    }

    /** Rotates the supplied bitmap according to the orientation tag stored in the exif data. If no
     *  rotation is required, the input bitmap is returned. If rotation is required, the input
     *  bitmap is recycled.
     * @param uri Uri containing the JPEG with Exif information to use.
     */
    public static Bitmap rotateForExif(Context context, Bitmap bitmap, Uri uri) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "rotateForExif");
        ExifInterface exif;
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            exif = new ExifInterface(inputStream);
        }
        finally {
            if( inputStream != null )
                inputStream.close();
        }

        if( exif != null ) {
            bitmap = rotateForExif(bitmap, exif);
        }
        return bitmap;
    }

}

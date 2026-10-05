package net.sourceforge.opencamera;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.RawImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;

import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

/** Handles the saving (and any required processing) of photos.
 */
public class ImageSaver extends Thread {
    private static final String TAG = "ImageSaver";

    static final String hdr_suffix = "_HDR";
    static final String nr_suffix = "_NR";
    static final String pano_suffix = "_PANO";

    private final MainActivity main_activity;

    // components
    private final HDRProcessor hdrProcessor;
    private final PanoramaProcessor panoramaProcessor;
    private final PostProcessing postProcessing;

    /* We use a separate count n_images_to_save, rather than just relying on the queue size, so we can take() an image from queue,
     * but only decrement the count when we've finished saving the image.
     * In general, n_images_to_save represents the number of images still to process, including ones currently being processed.
     * Therefore we should always have n_images_to_save >= queue.size().
     * Also note, main_activity.imageQueueChanged() should be called on UI thread after n_images_to_save increases or
     * decreases.
     * Access to n_images_to_save should always be synchronized to this (i.e., the ImageSaver class).
     * n_real_images_to_save excludes "Dummy" or "on_destroy" requests, and should also be synchronized, and modified
     * at the same time as n_images_to_save.
     */
    private int n_images_to_save = 0;
    private int n_real_images_to_save = 0;
    private final int queue_capacity;
    private final BlockingQueue<Request> queue;
    private final static int queue_cost_jpeg_c = 1; // also covers WEBP
    private final static int queue_cost_dng_c = 6;
    //private final static int queue_cost_dng_c = 1;

    // Should be same as MainActivity.app_is_paused, but we keep our own copy to make threading easier (otherwise, all
    // accesses of MainActivity.app_is_paused would need to be synchronized).
    // Access to app_is_paused should always be synchronized to this (i.e., the ImageSaver class).
    private boolean app_is_paused = true;

    // for testing; must be volatile for test project reading the state
    // n.b., avoid using static, as static variables are shared between different instances of an application,
    // and won't be reset in subsequent tests in a suite!
    public static volatile boolean test_small_queue_size; // needs to be static, as it needs to be set before activity is created to take effect
    public volatile boolean test_slow_saving;
    public volatile boolean test_queue_blocked;

    static class Request {
        enum Type {
            JPEG, // also covers WEBP
            RAW,
            DUMMY,
            ON_DESTROY // indicate that application is being destroyed, so should exit thread
        }
        final Type type;
        enum ProcessType {
            NORMAL,
            HDR, // also covers DRO, if only 1 image in the request
            AVERAGE,
            PANORAMA,
            X_NIGHT
        }
        final ProcessType process_type; // for type==JPEG
        final boolean force_suffix; // affects filename suffixes for saving jpeg_images: if true, filenames will always be appended with a suffix like _0, even if there's only 1 image in jpeg_images
        final int suffix_offset; // affects filename suffixes for saving jpeg_images, when force_suffix is true or there are multiple images in jpeg_images: the suffixes will be offset by this number
        enum SaveBase {
            SAVEBASE_NONE,
            SAVEBASE_FIRST,
            SAVEBASE_ALL,
            SAVEBASE_ALL_PLUS_DEBUG // for PANORAMA
        }
        final SaveBase save_base; // whether to save the base images, for process_type HDR, AVERAGE or PANORAMA
        /* jpeg_images: for jpeg (may be null otherwise).
         * If process_type==HDR, this should be 1 or 3 images, and the images are combined/converted to a HDR image (if there's only 1
         * image, this uses fake HDR or "DRO").
         * If process_type==NORMAL, then multiple images are saved sequentially.
         */
        final List<byte []> jpeg_images;
        final List<Bitmap> preshot_bitmaps; // if non-null, bitmaps for preshots; bitmaps will be recycled once processed
        final RawImage raw_image; // for raw
        final boolean image_capture_intent;
        final Uri image_capture_intent_uri;
        final boolean using_camera2;
        final boolean using_camera_extensions;
        /* image_format allows converting the standard JPEG image into another file format.
#        */
        enum ImageFormat {
            STD, // leave unchanged from the standard JPEG format
            WEBP,
            PNG
        }
        ImageFormat image_format;
        int image_quality;
        boolean do_auto_stabilise;
        final double level_angle; // in degrees
        final List<float []> gyro_rotation_matrix; // used for panorama (one 3x3 matrix per jpeg_images entry), otherwise can be null
        boolean panorama_dir_left_to_right; // used for panorama
        float camera_view_angle_x; // used for panorama
        float camera_view_angle_y; // used for panorama
        final boolean is_front_facing;
        boolean mirror;
        final Date current_date;
        final HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm; // for HDR
        final String preference_hdr_contrast_enhancement; // for HDR
        final int iso; // not applicable for RAW image
        final long exposure_time; // not applicable for RAW image
        final float zoom_factor; // not applicable for RAW image
        String preference_stamp;
        String preference_textstamp;
        final int font_size;
        final int color;
        final String pref_style;
        final String preference_stamp_dateformat;
        final String preference_stamp_timeformat;
        final String preference_stamp_gpsformat;
        //final String preference_stamp_geo_address;
        final String preference_units_distance;
        final boolean panorama_crop; // used for panorama
        enum RemoveDeviceExif {
            OFF, // don't remove any device exif tags
            ON, // remove all device exif tags
            KEEP_DATETIME // remove all device exif tags except datetime tags
        }
        final RemoveDeviceExif remove_device_exif;
        final boolean store_location;
        final Location location;
        final boolean store_geo_direction;
        final double geo_direction; // in radians
        final boolean store_ypr; // whether to store geo_angle, pitch_angle, level_angle in USER_COMMENT if exif (for JPEGs)
        final double pitch_angle; // the pitch that the phone is at, in degrees
        final String custom_tag_artist;
        final String custom_tag_copyright;
        final int sample_factor; // sampling factor for thumbnail, higher means lower quality

        Request(Type type,
                ProcessType process_type,
                boolean force_suffix,
                int suffix_offset,
                SaveBase save_base,
                List<byte []> jpeg_images,
                List<Bitmap> preshot_bitmaps,
                RawImage raw_image,
                boolean image_capture_intent, Uri image_capture_intent_uri,
                boolean using_camera2, boolean using_camera_extensions,
                ImageFormat image_format, int image_quality,
                boolean do_auto_stabilise, double level_angle, List<float []> gyro_rotation_matrix,
                boolean is_front_facing,
                boolean mirror,
                Date current_date,
                HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm,
                String preference_hdr_contrast_enhancement,
                int iso,
                long exposure_time,
                float zoom_factor,
                String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                //String preference_stamp_geo_address,
                String preference_units_distance,
                boolean panorama_crop,
                RemoveDeviceExif remove_device_exif,
                boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                double pitch_angle, boolean store_ypr,
                String custom_tag_artist,
                String custom_tag_copyright,
                int sample_factor) {
            this.type = type;
            this.process_type = process_type;
            this.force_suffix = force_suffix;
            this.suffix_offset = suffix_offset;
            this.save_base = save_base;
            this.jpeg_images = jpeg_images;
            this.preshot_bitmaps = preshot_bitmaps;
            this.raw_image = raw_image;
            this.image_capture_intent = image_capture_intent;
            this.image_capture_intent_uri = image_capture_intent_uri;
            this.using_camera2 = using_camera2;
            this.using_camera_extensions = using_camera_extensions;
            this.image_format = image_format;
            this.image_quality = image_quality;
            this.do_auto_stabilise = do_auto_stabilise;
            this.level_angle = level_angle;
            this.gyro_rotation_matrix = gyro_rotation_matrix;
            this.is_front_facing = is_front_facing;
            this.mirror = mirror;
            this.current_date = current_date;
            this.preference_hdr_tonemapping_algorithm = preference_hdr_tonemapping_algorithm;
            this.preference_hdr_contrast_enhancement = preference_hdr_contrast_enhancement;
            this.iso = iso;
            this.exposure_time = exposure_time;
            this.zoom_factor = zoom_factor;
            this.preference_stamp = preference_stamp;
            this.preference_textstamp = preference_textstamp;
            this.font_size = font_size;
            this.color = color;
            this.pref_style = pref_style;
            this.preference_stamp_dateformat = preference_stamp_dateformat;
            this.preference_stamp_timeformat = preference_stamp_timeformat;
            this.preference_stamp_gpsformat = preference_stamp_gpsformat;
            //this.preference_stamp_geo_address = preference_stamp_geo_address;
            this.preference_units_distance = preference_units_distance;
            this.panorama_crop = panorama_crop;
            this.remove_device_exif = remove_device_exif;
            this.store_location = store_location;
            this.location = location;
            this.store_geo_direction = store_geo_direction;
            this.geo_direction = geo_direction;
            this.pitch_angle = pitch_angle;
            this.store_ypr = store_ypr;
            this.custom_tag_artist = custom_tag_artist;
            this.custom_tag_copyright = custom_tag_copyright;
            this.sample_factor = sample_factor;
        }

        /** Returns a copy of this object. Note that it is not a deep copy - data such as JPEG and RAW
         *  data will not be copied.
         */
        Request copy() {
            return new Request(this.type,
                    this.process_type,
                    this.force_suffix,
                    this.suffix_offset,
                    this.save_base,
                    this.jpeg_images,
                    this.preshot_bitmaps,
                    this.raw_image,
                    this.image_capture_intent, this.image_capture_intent_uri,
                    this.using_camera2, this.using_camera_extensions,
                    this.image_format, this.image_quality,
                    this.do_auto_stabilise, this.level_angle, this.gyro_rotation_matrix,
                    this.is_front_facing,
                    this.mirror,
                    this.current_date,
                    this.preference_hdr_tonemapping_algorithm,
                    this.preference_hdr_contrast_enhancement,
                    this.iso,
                    this.exposure_time,
                    this.zoom_factor,
                    this.preference_stamp, this.preference_textstamp, this.font_size, this.color, this.pref_style, this.preference_stamp_dateformat, this.preference_stamp_timeformat, this.preference_stamp_gpsformat,
                    //this.preference_stamp_geo_address,
                    this.preference_units_distance,
                    this.panorama_crop, this.remove_device_exif, this.store_location, this.location, this.store_geo_direction, this.geo_direction,
                    this.pitch_angle, this.store_ypr,
                    this.custom_tag_artist,
                    this.custom_tag_copyright,
                    this.sample_factor);
        }
    }

    ImageSaver(MainActivity main_activity) {
        super("ImageSaver");
        if( MyDebug.LOG )
            Log.d(TAG, "ImageSaver");
        this.main_activity = main_activity;

        ActivityManager activityManager = (ActivityManager) main_activity.getSystemService(Activity.ACTIVITY_SERVICE);
        this.queue_capacity = computeQueueSize(activityManager.getLargeMemoryClass());
        this.queue = new ArrayBlockingQueue<>(queue_capacity); // since we remove from the queue and then process in the saver thread, in practice the number of background photos - including the one being processed - is one more than the length of this queue

        this.hdrProcessor = new HDRProcessor(main_activity, main_activity.is_test);
        this.panoramaProcessor = new PanoramaProcessor(main_activity, hdrProcessor);
        this.postProcessing = new PostProcessing(main_activity);
    }

    /** Returns the length of the image saver queue. In practice, the number of images that can be taken at once before the UI
     *  blocks is 1 more than this, as 1 image will be taken off the queue to process straight away.
     */
    public int getQueueSize() {
        return this.queue_capacity;
    }

    /** Compute a sensible size for the queue, based on the device's memory (large heap).
     */
    public static int computeQueueSize(int large_heap_memory) {
        if( MyDebug.LOG )
            Log.d(TAG, "large max memory = " + large_heap_memory + "MB");
        int max_queue_size;
        if( MyDebug.LOG )
            Log.d(TAG, "test_small_queue_size?: " + test_small_queue_size);
        if( test_small_queue_size ) {
            large_heap_memory = 0;
        }

        if( large_heap_memory >= 512 ) {
            // This should be at least 5*(queue_cost_jpeg_c+queue_cost_dng_c)-1 so we can take a burst of 5 photos
            // (e.g., in expo mode) with RAW+JPEG without blocking (we subtract 1, as the first image can be immediately
            // taken off the queue).
            // This should also be at least 19 so we can take a burst of 20 photos with JPEG without blocking (we subtract 1,
            // as the first image can be immediately taken off the queue).
            // This should be at most 70 for large heap 512MB (estimate based on reserving 160MB for post-processing and HDR
            // operations, then estimate a JPEG image at 5MB).
            max_queue_size = 34;
        }
        else if( large_heap_memory >= 256 ) {
            // This should be at most 19 for large heap 256MB.
            max_queue_size = 12;
        }
        else if( large_heap_memory >= 128 ) {
            // This should be at least 1*(queue_cost_jpeg_c+queue_cost_dng_c)-1 so we can take a photo with RAW+JPEG
            // without blocking (we subtract 1, as the first image can be immediately taken off the queue).
            // This should be at most 8 for large heap 128MB (allowing 80MB for post-processing).
            max_queue_size = 8;
        }
        else {
            // This should be at least 1*(queue_cost_jpeg_c+queue_cost_dng_c)-1 so we can take a photo with RAW+JPEG
            // without blocking (we subtract 1, as the first image can be immediately taken off the queue).
            max_queue_size = 6;
        }
        //max_queue_size = 1;
        //max_queue_size = 3;
        if( MyDebug.LOG )
            Log.d(TAG, "max_queue_size = " + max_queue_size);
        return max_queue_size;
    }

    /** Computes the cost for a particular request.
     *  Note that for RAW+DNG mode, computeRequestCost() is called twice for a given photo (one for each
     *  of the two requests: one RAW, one JPEG).
     * @param is_raw Whether RAW/DNG or JPEG.
     * @param n_images This is the number of JPEG or RAW images that are in the request.
     */
    public static int computeRequestCost(boolean is_raw, int n_images) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computeRequestCost");
            Log.d(TAG, "is_raw: " + is_raw);
            Log.d(TAG, "n_images: " + n_images);
        }
        int cost;
        if( is_raw )
            cost = n_images * queue_cost_dng_c;
        else {
            cost = n_images * queue_cost_jpeg_c;
            //cost = (n_images > 1 ? 2 : 1) * queue_cost_jpeg_c;
        }
        return cost;
    }

    /** Computes the cost (in terms of number of slots on the image queue) of a new photo.
     * @param n_raw The number of JPEGs that will be taken.
     * @param n_jpegs The number of JPEGs that will be taken.
     */
    int computePhotoCost(int n_raw, int n_jpegs) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computePhotoCost");
            Log.d(TAG, "n_raw: " + n_raw);
            Log.d(TAG, "n_jpegs: " + n_jpegs);
        }
        int cost = 0;
        if( n_raw > 0 )
            cost += computeRequestCost(true, n_raw);
        if( n_jpegs > 0 )
            cost += computeRequestCost(false, n_jpegs);
        if( MyDebug.LOG )
            Log.d(TAG, "cost: " + cost);
        return cost;
    }

    /** Whether taking an extra photo would overflow the queue, resulting in the UI hanging.
     * @param n_raw The number of JPEGs that will be taken.
     * @param n_jpegs The number of JPEGs that will be taken.
     */
    boolean queueWouldBlock(int n_raw, int n_jpegs) {
        int photo_cost = this.computePhotoCost(n_raw, n_jpegs);
        return this.queueWouldBlock(photo_cost);
    }

    /** Whether taking an extra photo would overflow the queue, resulting in the UI hanging.
     * @param photo_cost The result returned by computePhotoCost().
     */
    synchronized boolean queueWouldBlock(int photo_cost) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "queueWouldBlock");
            Log.d(TAG, "photo_cost: " + photo_cost);
            Log.d(TAG, "n_images_to_save: " + n_images_to_save);
            Log.d(TAG, "queue_capacity: " + queue_capacity);
        }
        // we add one to queue, to account for the image currently being processed; n_images_to_save includes an image
        // currently being processed
        if( n_images_to_save == 0 ) {
            // In theory, we should never have the extra_cost large enough to block the queue even when no images are being
            // saved - but we have this just in case. This means taking the photo will likely block the UI, but we don't want
            // to disallow ever taking photos!
            if( MyDebug.LOG )
                Log.d(TAG, "queue is empty");
            return false;
        }
        else if( n_images_to_save + photo_cost > queue_capacity + 1 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "queue would block");
            return true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "queue would not block");
        return false;
    }

    /** Returns the maximum number of DNG images that might be held by the image saver queue at once, before blocking.
     */
    int getMaxDNG() {
        int max_dng = (queue_capacity+1)/queue_cost_dng_c;
        max_dng++; // increase by 1, as the user can still take one extra photo if the queue is exactly full
        if( MyDebug.LOG )
            Log.d(TAG, "max_dng = " + max_dng);
        return max_dng;
    }

    /** Returns the number of images to save, weighted by their cost (e.g., so a single RAW image
     *  will be counted as multiple images).
     */
    public synchronized int getNImagesToSave() {
        return n_images_to_save;
    }

    /** Returns the number of images to save (e.g., so a single RAW image will only be counted as
     *  one image, unlike getNImagesToSave()).

     */
    public synchronized int getNRealImagesToSave() {
        return n_real_images_to_save;
    }

    /** Application has paused.
     */
    void onPause() {
        synchronized(this) {
            app_is_paused = true;
        }
    }

    /** Application has resumed.
     */
    void onResume() {
        synchronized(this) {
            app_is_paused = false;
        }
    }

    void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        {
            //  a request so that the imagesaver thread will complete
            Request request = new Request(Request.Type.ON_DESTROY,
                    Request.ProcessType.NORMAL,
                    false,
                    0,
                    Request.SaveBase.SAVEBASE_NONE,
                    null,
                    null,
                    null,
                    false, null,
                    false, false,
                    Request.ImageFormat.STD, 0,
                    false, 0.0, null,
                    false,
                    false,
                    null,
                    HDRProcessor.default_tonemapping_algorithm_c,
                    null,
                    0,
                    0,
                    1.0f,
                    null, null, 0, 0, null, null, null, null,
                    //null,
                    null,
                    false, Request.RemoveDeviceExif.OFF, false, null, false, 0.0,
                    0.0, false,
                    null, null,
                    1);
            if( MyDebug.LOG )
                Log.d(TAG, "add on_destroy request");
            addRequest(request, 1);
        }
        if( panoramaProcessor != null ) {
            panoramaProcessor.onDestroy();
        }
        if( hdrProcessor != null ) {
            hdrProcessor.onDestroy();
        }
    }

    @Override
    public void run() {
        if( MyDebug.LOG )
            Log.d(TAG, "starting ImageSaver thread...");
        while( true ) {
            try {
                if( MyDebug.LOG )
                    Log.d(TAG, "ImageSaver thread reading from queue, size: " + queue.size());
                Request request = queue.take(); // if empty, take() blocks until non-empty
                // Only decrement n_images_to_save after we've actually saved the image! Otherwise waitUntilDone() will return
                // even though we still have a last image to be saved.
                if( MyDebug.LOG )
                    Log.d(TAG, "ImageSaver thread found new request from queue, size is now: " + queue.size());
                boolean success;
                boolean on_destroy = false;
                switch (request.type) {
                    case RAW:
                        if (MyDebug.LOG)
                            Log.d(TAG, "request is raw");
                        success = saveImageNowRaw(request);
                        break;
                    case JPEG:
                        if (MyDebug.LOG)
                            Log.d(TAG, "request is jpeg");
                        success = saveImageNow(request);
                        break;
                    case DUMMY:
                        if (MyDebug.LOG)
                            Log.d(TAG, "request is dummy");
                        success = true;
                        break;
                    case ON_DESTROY:
                        if( MyDebug.LOG )
                            Log.d(TAG, "request is on_destroy");
                        success = true;
                        on_destroy = true;
                        break;
                    default:
                        if (MyDebug.LOG)
                            Log.e(TAG, "request is unknown type!");
                        success = false;
                        break;
                }
                if( test_slow_saving ) {
                    // ignore warning about "Call to Thread.sleep in a loop", this is only activated in test code
                    //noinspection BusyWait
                    Thread.sleep(2000);
                }
                if( MyDebug.LOG ) {
                    if( success )
                        Log.d(TAG, "ImageSaver thread successfully saved image");
                    else
                        Log.e(TAG, "ImageSaver thread failed to save image");
                }
                synchronized( this ) {
                    n_images_to_save--;
                    if( request.type != Request.Type.DUMMY && request.type != Request.Type.ON_DESTROY )
                        n_real_images_to_save--;
                    if( MyDebug.LOG )
                        Log.d(TAG, "ImageSaver thread processed new request from queue, images to save is now: " + n_images_to_save);
                    if( MyDebug.LOG && n_images_to_save < 0 ) {
                        Log.e(TAG, "images to save has become negative");
                        throw new RuntimeException();
                    }
                    else if( MyDebug.LOG && n_real_images_to_save < 0 ) {
                        Log.e(TAG, "real images to save has become negative");
                        throw new RuntimeException();
                    }
                    notifyAll();

                    main_activity.runOnUiThread(new Runnable() {
                        public void run() {
                            main_activity.imageQueueChanged();
                        }
                    });
                }
                if( on_destroy ) {
                    break;
                }
            }
            catch(InterruptedException e) {
                MyDebug.logStackTrace(TAG, "interrupted while trying to read from ImageSaver queue", e);
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "stopping ImageSaver thread...");
    }

    /** Saves a photo.
     *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
     *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
     *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
     *  successfully.
     */
    boolean saveImageJpeg(boolean do_in_background,
                          Request.ProcessType processType,
                          boolean force_suffix,
                          int suffix_offset,
                          boolean save_expo,
                          List<byte []> images,
                          List<Bitmap> preshot_bitmaps,
                          boolean image_capture_intent, Uri image_capture_intent_uri,
                          boolean using_camera2, boolean using_camera_extensions,
                          Request.ImageFormat image_format, int image_quality,
                          boolean do_auto_stabilise, double level_angle,
                          boolean is_front_facing,
                          boolean mirror,
                          Date current_date,
                          HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm,
                          String preference_hdr_contrast_enhancement,
                          int iso,
                          long exposure_time,
                          float zoom_factor,
                          String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                          //String preference_stamp_geo_address,
                          String preference_units_distance,
                          boolean panorama_crop,
                          Request.RemoveDeviceExif remove_device_exif,
                          boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                          double pitch_angle, boolean store_ypr,
                          String custom_tag_artist,
                          String custom_tag_copyright,
                          int sample_factor) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "saveImageJpeg");
            Log.d(TAG, "do_in_background? " + do_in_background);
            Log.d(TAG, "number of images: " + images.size());
        }
        return saveImage(do_in_background,
                false,
                processType,
                force_suffix,
                suffix_offset,
                save_expo,
                images,
                preshot_bitmaps,
                null,
                image_capture_intent, image_capture_intent_uri,
                using_camera2, using_camera_extensions,
                image_format, image_quality,
                do_auto_stabilise, level_angle,
                is_front_facing,
                mirror,
                current_date,
                preference_hdr_tonemapping_algorithm,
                preference_hdr_contrast_enhancement,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
                //preference_stamp_geo_address,
                preference_units_distance,
                panorama_crop, remove_device_exif, store_location, location, store_geo_direction, geo_direction,
                pitch_angle, store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor);
    }

    /** Saves a RAW photo.
     *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
     *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
     *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
     *  successfully.
     */
    boolean saveImageRaw(boolean do_in_background,
                         boolean force_suffix,
                         int suffix_offset,
                         RawImage raw_image,
                         Date current_date) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "saveImageRaw");
            Log.d(TAG, "do_in_background? " + do_in_background);
        }
        return saveImage(do_in_background,
                true,
                Request.ProcessType.NORMAL,
                force_suffix,
                suffix_offset,
                false,
                null,
                null,
                raw_image,
                false, null,
                false, false,
                Request.ImageFormat.STD, 0,
                false, 0.0,
                false,
                false,
                current_date,
                HDRProcessor.default_tonemapping_algorithm_c,
                null,
                0,
                0,
                1.0f,
                null, null, 0, 0, null, null, null, null,
                //null,
                null,
                false, Request.RemoveDeviceExif.OFF, false, null, false, 0.0,
                0.0, false,
                null, null,
                1);
    }

    private Request pending_image_average_request = null;

    /** Used for a batch of images that will be combined into a single request. This applies to
     *  processType AVERAGE and PANORAMA.
     */
    void startImageBatch(boolean do_in_background,
                           Request.ProcessType processType,
                           List<Bitmap> preshot_bitmaps,
                           Request.SaveBase save_base,
                           boolean image_capture_intent, Uri image_capture_intent_uri,
                           boolean using_camera2, boolean using_camera_extensions,
                           Request.ImageFormat image_format, int image_quality,
                           boolean do_auto_stabilise, double level_angle, boolean want_gyro_matrices,
                           boolean is_front_facing,
                           boolean mirror,
                           Date current_date,
                           int iso,
                           long exposure_time,
                           float zoom_factor,
                           String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                           //String preference_stamp_geo_address,
                           String preference_units_distance,
                           boolean panorama_crop,
                           Request.RemoveDeviceExif remove_device_exif,
                           boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                           double pitch_angle, boolean store_ypr,
                           String custom_tag_artist,
                           String custom_tag_copyright,
                           int sample_factor) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "startImageBatch");
            Log.d(TAG, "do_in_background? " + do_in_background);
        }
        pending_image_average_request = new Request(Request.Type.JPEG,
                processType,
                false,
                0,
                save_base,
                new ArrayList<>(),
                preshot_bitmaps,
                null,
                image_capture_intent, image_capture_intent_uri,
                using_camera2, using_camera_extensions,
                image_format, image_quality,
                do_auto_stabilise, level_angle, want_gyro_matrices ? new ArrayList<>() : null,
                is_front_facing,
                mirror,
                current_date,
                HDRProcessor.default_tonemapping_algorithm_c,
                null,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
                //preference_stamp_geo_address,
                preference_units_distance,
                panorama_crop, remove_device_exif, store_location, location, store_geo_direction, geo_direction,
                pitch_angle, store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor);
    }

    void addImageBatch(byte [] image, float [] gyro_rotation_matrix) {
        if( MyDebug.LOG )
            Log.d(TAG, "addImageBatch");
        if( pending_image_average_request == null ) {
            Log.e(TAG, "addImageBatch called but no pending_image_average_request");
            return;
        }
        pending_image_average_request.jpeg_images.add(image);
        if( gyro_rotation_matrix != null ) {
            float [] copy = new float[gyro_rotation_matrix.length];
            System.arraycopy(gyro_rotation_matrix, 0, copy, 0, gyro_rotation_matrix.length);
            pending_image_average_request.gyro_rotation_matrix.add(copy);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "image average request images: " + pending_image_average_request.jpeg_images.size());
    }

    Request getImageBatchRequest() {
        return pending_image_average_request;
    }

    void finishImageBatch(boolean do_in_background) {
        if( MyDebug.LOG )
            Log.d(TAG, "finishImageBatch");
        if( pending_image_average_request == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "finishImageBatch called but no pending_image_average_request");
            return;
        }
        if( do_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "add background request");
            int cost = computeRequestCost(false, pending_image_average_request.jpeg_images.size());
            addRequest(pending_image_average_request, cost);
        }
        else {
            // wait for queue to be empty
            waitUntilDone();
            saveImageNow(pending_image_average_request);
        }
        pending_image_average_request = null;
    }

    void flushImageBatch() {
        if( MyDebug.LOG )
            Log.d(TAG, "flushImageBatch");
        // aside from resetting the state, this allows the allocated JPEG data to be garbage collected
        pending_image_average_request = null;
    }

    /** Internal saveImage method to handle both JPEG and RAW.
     */
    private boolean saveImage(boolean do_in_background,
                              boolean is_raw,
                              Request.ProcessType processType,
                              boolean force_suffix,
                              int suffix_offset,
                              boolean save_expo,
                              List<byte []> jpeg_images,
                              List<Bitmap> preshot_bitmaps,
                              RawImage raw_image,
                              boolean image_capture_intent, Uri image_capture_intent_uri,
                              boolean using_camera2, boolean using_camera_extensions,
                              Request.ImageFormat image_format, int image_quality,
                              boolean do_auto_stabilise, double level_angle,
                              boolean is_front_facing,
                              boolean mirror,
                              Date current_date,
                              HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm,
                              String preference_hdr_contrast_enhancement,
                              int iso,
                              long exposure_time,
                              float zoom_factor,
                              String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                              //String preference_stamp_geo_address,
                              String preference_units_distance,
                              boolean panorama_crop,
                              Request.RemoveDeviceExif remove_device_exif,
                              boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                              double pitch_angle, boolean store_ypr,
                              String custom_tag_artist,
                              String custom_tag_copyright,
                              int sample_factor) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "saveImage");
            Log.d(TAG, "do_in_background? " + do_in_background);
        }
        boolean success;

        //do_in_background = false;

        Request request = new Request(is_raw ? Request.Type.RAW : Request.Type.JPEG,
                processType,
                force_suffix,
                suffix_offset,
                save_expo ? Request.SaveBase.SAVEBASE_ALL : Request.SaveBase.SAVEBASE_NONE,
                jpeg_images,
                preshot_bitmaps,
                raw_image,
                image_capture_intent, image_capture_intent_uri,
                using_camera2, using_camera_extensions,
                image_format, image_quality,
                do_auto_stabilise, level_angle, null,
                is_front_facing,
                mirror,
                current_date,
                preference_hdr_tonemapping_algorithm,
                preference_hdr_contrast_enhancement,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
                //preference_stamp_geo_address,
                preference_units_distance,
                panorama_crop, remove_device_exif, store_location, location, store_geo_direction, geo_direction,
                pitch_angle, store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor);

        if( do_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "add background request");
            int cost = computeRequestCost(is_raw, is_raw ? 1 : request.jpeg_images.size());
            addRequest(request, cost);
            success = true; // always return true when done in background
        }
        else {
            // wait for queue to be empty
            waitUntilDone();
            if( is_raw ) {
                success = saveImageNowRaw(request);
            }
            else {
                success = saveImageNow(request);
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "success: " + success);
        return success;
    }

    /** Adds a request to the background queue, blocking if the queue is already full
     */
    private void addRequest(Request request, int cost) {
        if( MyDebug.LOG )
            Log.d(TAG, "addRequest, cost: " + cost);
        if( main_activity.isDestroyed() && request.type != Request.Type.ON_DESTROY ) {
            // If the application is being destroyed as a new photo is being taken, it's not safe to continue, unless this request
            // is for the ON_DESTROY
            // MainDestroy.onDestroy() does call waitUntilDone(), but this is extra protection in case an image comes in after that.
            Log.e(TAG, "application is destroyed, image lost!");
            return;
        }
        // this should not be synchronized on "this": BlockingQueue is thread safe, and if it's blocking in queue.put(), we'll hang because
        // the saver queue will need to synchronize on "this" in order to notifyAll() the main thread
        boolean done = false;
        while( !done ) {
            try {
                if( MyDebug.LOG )
                    Log.d(TAG, "ImageSaver thread adding to queue, size: " + queue.size());
                synchronized( this ) {
                    // see above for why we don't synchronize the queue.put call
                    // but we synchronize modification to avoid risk of problems related to compiler optimisation (local caching or reordering)
                    // also see FindBugs warning due to inconsistent synchronisation
                    n_images_to_save++; // increment before adding to the queue, just to make sure the main thread doesn't think we're all done
                    if( request.type != Request.Type.DUMMY && request.type != Request.Type.ON_DESTROY )
                        n_real_images_to_save++;

                    main_activity.runOnUiThread(new Runnable() {
                        public void run() {
                            main_activity.imageQueueChanged();
                        }
                    });
                }
                if( queue.size() + 1 > queue_capacity ) {
                    Log.e(TAG, "ImageSaver thread is going to block, queue already full: " + queue.size());
                    test_queue_blocked = true;
                    //throw new RuntimeException(); // test
                }
                queue.put(request); // if queue is full, put() blocks until it isn't full
                if( MyDebug.LOG ) {
                    synchronized( this ) { // keep FindBugs happy
                        Log.d(TAG, "ImageSaver thread added to queue, size is now: " + queue.size());
                        Log.d(TAG, "images still to save is now: " + n_images_to_save);
                        Log.d(TAG, "real images still to save is now: " + n_real_images_to_save);
                    }
                }
                done = true;
            }
            catch(InterruptedException e) {
                MyDebug.logStackTrace(TAG, "interrupted while trying to add to ImageSaver queue", e);
            }
        }
        if( cost > 0 ) {
            // add "dummy" requests to simulate the cost
            for(int i=0;i<cost-1;i++) {
                addDummyRequest();
            }
        }
    }

    private void addDummyRequest() {
        Request dummy_request = new Request(Request.Type.DUMMY,
                Request.ProcessType.NORMAL,
                false,
                0,
                Request.SaveBase.SAVEBASE_NONE,
                null,
                null,
                null,
                false, null,
                false, false,
                Request.ImageFormat.STD, 0,
                false, 0.0, null,
                false,
                false,
                null,
                HDRProcessor.default_tonemapping_algorithm_c,
                null,
                0,
                0,
                1.0f,
                null, null, 0, 0, null, null, null, null,
                //null,
                null,
                false, Request.RemoveDeviceExif.OFF, false, null, false, 0.0,
                0.0, false,
                null, null,
                1);
        if( MyDebug.LOG )
            Log.d(TAG, "add dummy request");
        addRequest(dummy_request, 1); // cost must be 1, so we don't have infinite recursion!
    }

    /** Wait until the queue is empty and all pending images have been saved.
     */
    void waitUntilDone() {
        if( MyDebug.LOG )
            Log.d(TAG, "waitUntilDone");
        synchronized( this ) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "waitUntilDone: queue is size " + queue.size());
                Log.d(TAG, "waitUntilDone: images still to save " + n_images_to_save);
            }
            while( n_images_to_save > 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "wait until done...");
                try {
                    wait();
                }
                catch(InterruptedException e) {
                    MyDebug.logStackTrace(TAG, "interrupted while waiting for ImageSaver queue to be empty", e);
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "waitUntilDone: queue is size " + queue.size());
                    Log.d(TAG, "waitUntilDone: images still to save " + n_images_to_save);
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "waitUntilDone: images all saved");
    }

    /** Chooses the hdr_alpha to use for contrast enhancement in the HDR algorithm, based on the user
     *  preferences and scene details.
     */
    public static float getHDRAlpha(String preference_hdr_contrast_enhancement, long exposure_time, int n_bitmaps) {
        boolean use_hdr_alpha;
        if( n_bitmaps == 1 ) {
            // DRO always applies hdr_alpha
            use_hdr_alpha = true;
        }
        else {
            // else HDR
            switch( preference_hdr_contrast_enhancement ) {
                case "preference_hdr_contrast_enhancement_off":
                    use_hdr_alpha = false;
                    break;
                case "preference_hdr_contrast_enhancement_always":
                    use_hdr_alpha = true;
                    break;
                case "preference_hdr_contrast_enhancement_smart":
                default:
                    // Using local contrast enhancement helps scenes where the dynamic range is very large, which tends to be when we choose
                    // a short exposure time, due to fixing problems where some regions are too dark.
                    // This helps: testHDR11, testHDR19, testHDR34, testHDR53, testHDR61.
                    // Using local contrast enhancement in all cases can increase noise in darker scenes. This problem would occur
                    // (if we used local contrast enhancement) is: testHDR2, testHDR12, testHDR17, testHDR43, testHDR50, testHDR51,
                    // testHDR54, testHDR55, testHDR56.
                    use_hdr_alpha = (exposure_time < 1000000000L/59);
                    break;
            }
        }
        //use_hdr_alpha = true; // test
        float hdr_alpha = use_hdr_alpha ? 0.5f : 0.0f;
        if( MyDebug.LOG ) {
            Log.d(TAG, "preference_hdr_contrast_enhancement: " + preference_hdr_contrast_enhancement);
            Log.d(TAG, "exposure_time: " + exposure_time);
            Log.d(TAG, "hdr_alpha: " + hdr_alpha);
        }
        return hdr_alpha;
    }

    private final static String gyro_info_doc_tag = "open_camera_gyro_info";
    private final static String gyro_info_panorama_pics_per_screen_tag = "panorama_pics_per_screen";
    private final static String gyro_info_camera_view_angle_x_tag = "camera_view_angle_x";
    private final static String gyro_info_camera_view_angle_y_tag = "camera_view_angle_y";
    private final static String gyro_info_image_tag = "image";
    private final static String gyro_info_vector_tag = "vector";
    private final static String gyro_info_vector_right_type = "X";
    private final static String gyro_info_vector_up_type = "Y";
    private final static String gyro_info_vector_screen_type = "Z";

    private void writeGyroDebugXml(Writer writer, Request request) throws IOException {
        XmlSerializer xmlSerializer = Xml.newSerializer();

        xmlSerializer.setOutput(writer);
        xmlSerializer.startDocument("UTF-8", true);
        xmlSerializer.startTag(null, gyro_info_doc_tag);
        xmlSerializer.attribute(null, gyro_info_panorama_pics_per_screen_tag, String.valueOf(MyApplicationInterface.getPanoramaPicsPerScreen()));
        xmlSerializer.attribute(null, gyro_info_camera_view_angle_x_tag, String.valueOf(request.camera_view_angle_x));
        xmlSerializer.attribute(null, gyro_info_camera_view_angle_y_tag, String.valueOf(request.camera_view_angle_y));

        float [] inVector = new float[3];
        float [] outVector = new float[3];
        for(int i=0;i<request.gyro_rotation_matrix.size();i++) {
            xmlSerializer.startTag(null, gyro_info_image_tag);
            xmlSerializer.attribute(null, "index", String.valueOf(i));

            GyroSensor.setVector(inVector, 1.0f, 0.0f, 0.0f); // vector pointing in "right" direction
            GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
            xmlSerializer.startTag(null, gyro_info_vector_tag);
            xmlSerializer.attribute(null, "type", gyro_info_vector_right_type);
            xmlSerializer.attribute(null, "x", String.valueOf(outVector[0]));
            xmlSerializer.attribute(null, "y", String.valueOf(outVector[1]));
            xmlSerializer.attribute(null, "z", String.valueOf(outVector[2]));
            xmlSerializer.endTag(null, gyro_info_vector_tag);

            GyroSensor.setVector(inVector, 0.0f, 1.0f, 0.0f); // vector pointing in "up" direction
            GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
            xmlSerializer.startTag(null, gyro_info_vector_tag);
            xmlSerializer.attribute(null, "type", gyro_info_vector_up_type);
            xmlSerializer.attribute(null, "x", String.valueOf(outVector[0]));
            xmlSerializer.attribute(null, "y", String.valueOf(outVector[1]));
            xmlSerializer.attribute(null, "z", String.valueOf(outVector[2]));
            xmlSerializer.endTag(null, gyro_info_vector_tag);

            GyroSensor.setVector(inVector, 0.0f, 0.0f, -1.0f); // vector pointing behind the device's screen
            GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
            xmlSerializer.startTag(null, gyro_info_vector_tag);
            xmlSerializer.attribute(null, "type", gyro_info_vector_screen_type);
            xmlSerializer.attribute(null, "x", String.valueOf(outVector[0]));
            xmlSerializer.attribute(null, "y", String.valueOf(outVector[1]));
            xmlSerializer.attribute(null, "z", String.valueOf(outVector[2]));
            xmlSerializer.endTag(null, gyro_info_vector_tag);

            xmlSerializer.endTag(null, gyro_info_image_tag);
        }

        xmlSerializer.endTag(null, gyro_info_doc_tag);
        xmlSerializer.endDocument();
        xmlSerializer.flush();
    }

    @SuppressWarnings("WeakerAccess")
    public static class GyroDebugInfo {
        public static class GyroImageDebugInfo {
            public float [] vectorRight; // X axis
            public float [] vectorUp; // Y axis
            public float [] vectorScreen; // vector into the screen - actually the -Z axis
        }

        public final List<GyroImageDebugInfo> image_info;

        public GyroDebugInfo() {
            image_info = new ArrayList<>();
        }
    }

    public static boolean readGyroDebugXml(InputStream inputStream, GyroDebugInfo info) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();

            parser.require(XmlPullParser.START_TAG, null, gyro_info_doc_tag);
            GyroDebugInfo.GyroImageDebugInfo image_info = null;

            while( parser.next() != XmlPullParser.END_DOCUMENT ) {
                switch( parser.getEventType() ) {
                    case XmlPullParser.START_TAG: {
                        String name = parser.getName();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "start tag, name: " + name);
                        }

                        switch( name ) {
                            case gyro_info_image_tag:
                                info.image_info.add( image_info = new GyroDebugInfo.GyroImageDebugInfo() );
                                break;
                            case gyro_info_vector_tag:
                                if( image_info == null ) {
                                    Log.e(TAG, "vector tag outside of image tag");
                                    return false;
                                }
                                String type = parser.getAttributeValue(null, "type");
                                String x_s = parser.getAttributeValue(null, "x");
                                String y_s = parser.getAttributeValue(null, "y");
                                String z_s = parser.getAttributeValue(null, "z");
                                float [] vector = new float[3];
                                vector[0] = Float.parseFloat(x_s);
                                vector[1] = Float.parseFloat(y_s);
                                vector[2] = Float.parseFloat(z_s);
                                switch( type ) {
                                    case gyro_info_vector_right_type:
                                        image_info.vectorRight = vector;
                                        break;
                                    case gyro_info_vector_up_type:
                                        image_info.vectorUp = vector;
                                        break;
                                    case gyro_info_vector_screen_type:
                                        image_info.vectorScreen = vector;
                                        break;
                                    default:
                                        Log.e(TAG, "unknown type in vector tag: " + type);
                                        return false;
                                }
                                break;
                        }
                        break;
                    }
                    case XmlPullParser.END_TAG: {
                        String name = parser.getName();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "end tag, name: " + name);
                        }

                        //noinspection SwitchStatementWithTooFewBranches
                        switch( name ) {
                            case gyro_info_image_tag:
                                image_info = null;
                                break;
                        }
                        break;
                    }
                }
            }
        }
        catch(Exception e) {
            MyDebug.logStackTrace(TAG, "failed to parse xml", e);
            return false;
        }
        finally {
            try {
                inputStream.close();
            }
            catch(IOException e) {
                MyDebug.logStackTrace(TAG, "failed to close inputStream", e);
            }
        }
        return true;
    }

    private boolean processHDR(List<Bitmap> bitmaps, final Request request, long time_s) {
        float hdr_alpha = getHDRAlpha(request.preference_hdr_contrast_enhancement, request.exposure_time, bitmaps.size());
        if( MyDebug.LOG )
            Log.d(TAG, "before HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
        try {
            hdrProcessor.processHDR(bitmaps, true, null, true, null, hdr_alpha, 4, true, request.preference_hdr_tonemapping_algorithm, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA); // this will recycle all the bitmaps except bitmaps.get(0), which will contain the hdr image
        }
        catch(HDRProcessorException e) {
            MyDebug.logStackTrace(TAG, "HDRProcessorException from processHDR", e);
            if( e.getCode() == HDRProcessorException.UNEQUAL_SIZES ) {
                // this can happen on OnePlus 3T with old camera API with front camera, seems to be a bug that resolution changes when exposure compensation is set!
                Log.e(TAG, "UNEQUAL_SIZES");
                bitmaps.clear();
                System.gc();
                return false;
            }
            else {
                // throw RuntimeException, as we shouldn't ever get the error INVALID_N_IMAGES, if we do it's a programming error
                throw new RuntimeException();
            }
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "HDR performance: time after creating HDR image: " + (System.currentTimeMillis() - time_s));
        }
        if( MyDebug.LOG )
            Log.d(TAG, "after HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
        return true;
    }

    /** May be run in saver thread or picture callback thread (depending on whether running in background).
     */
    private boolean saveImageNow(final Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveImageNow");

        if( request.type != Request.Type.JPEG ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveImageNow called with non-jpeg request");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }
        else if( request.jpeg_images.isEmpty() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveImageNow called with zero images");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }

        if( request.preshot_bitmaps != null && !request.preshot_bitmaps.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            Preshots.savePreshotBitmaps(main_activity, this, request);
        }

        boolean success;
        if( request.process_type == Request.ProcessType.AVERAGE ) {
            if( MyDebug.LOG )
                Log.d(TAG, "average");

            saveBaseImages(request, "_");
            main_activity.savingImage(true);

            /*List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, 0);
            if (bitmaps == null) {
                if (MyDebug.LOG)
                    Log.e(TAG, "failed to load bitmaps");
                main_activity.savingImage(false);
                return false;
            }*/
            /*Bitmap nr_bitmap = loadBitmap(request.jpeg_images.get(0), true);

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
                try {
                    for(int i = 1; i < request.jpeg_images.size(); i++) {
                        Log.d(TAG, "processAvg for image: " + i);
                        Bitmap new_bitmap = loadBitmap(request.jpeg_images.get(i), false);
                        float avg_factor = (float) i;
                        hdrProcessor.processAvg(nr_bitmap, new_bitmap, avg_factor, true);
                        // processAvg recycles new_bitmap
                    }
                    //hdrProcessor.processAvgMulti(bitmaps, hdr_strength, 4);
                    //hdrProcessor.avgBrighten(nr_bitmap);
                }
                catch(HDRProcessorException e) {
                    MyDebug.logStackTrace(TAG, "HDRProcessorException from processAvg", e);
                    throw new RuntimeException();
                }
            }
            else {
                Log.e(TAG, "shouldn't have offered NoiseReduction as an option if not on Android 5");
                throw new RuntimeException();
            }*/
            Bitmap nr_bitmap;
            {
                try {
                    long time_s = System.currentTimeMillis();
                    int inSampleSize = hdrProcessor.getAvgSampleSize(request.iso, request.exposure_time);
                    //final boolean use_smp = false;
                    final boolean use_smp = true;
                    // n_smp_images is how many bitmaps to decompress at once if use_smp==true. Beware of setting too high -
                    // e.g., storing 4 16MP bitmaps takes 256MB of heap (NR requires at least 512MB large heap); also need to make
                    // sure there isn't a knock on effect on performance
                    //final int n_smp_images = 2;
                    final int n_smp_images = 4;
                    long this_time_s = System.currentTimeMillis();
                    List<Bitmap> bitmaps = null;
                    Bitmap bitmap0, bitmap1;
                    if( use_smp ) {
                        /*List<byte []> sub_jpeg_list = new ArrayList<>();
                        sub_jpeg_list.add(request.jpeg_images.get(0));
                        sub_jpeg_list.add(request.jpeg_images.get(1));
                        bitmaps = loadBitmaps(sub_jpeg_list, -1, inSampleSize);
                        bitmap0 = bitmaps.get(0);
                        bitmap1 = bitmaps.get(1);*/
                        int n_remaining = request.jpeg_images.size();
                        int n_load = Math.min(n_smp_images, n_remaining);
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "n_remaining: " + n_remaining);
                            Log.d(TAG, "n_load: " + n_load);
                        }
                        List<byte []> sub_jpeg_list = new ArrayList<>();
                        for(int j=0;j<n_load;j++) {
                            sub_jpeg_list.add(request.jpeg_images.get(j));
                        }
                        bitmaps = ImageUtils.loadBitmaps(sub_jpeg_list, -1, inSampleSize);
                        if( MyDebug.LOG )
                            Log.d(TAG, "length of bitmaps list is now: " + bitmaps.size());
                        bitmap0 = bitmaps.get(0);
                        bitmap1 = bitmaps.get(1);
                    }
                    else {
                        bitmap0 = ImageUtils.loadBitmap(request.jpeg_images.get(0), false, inSampleSize);
                        bitmap1 = ImageUtils.loadBitmap(request.jpeg_images.get(1), false, inSampleSize);
                    }
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** time for loading first bitmaps: " + (System.currentTimeMillis() - this_time_s));
                    }
                    int width = bitmap0.getWidth();
                    int height = bitmap0.getHeight();
                    float avg_factor = 1.0f;
                    this_time_s = System.currentTimeMillis();
                    HDRProcessor.AvgData avg_data = hdrProcessor.processAvg(bitmap0, bitmap1, avg_factor, request.iso, request.exposure_time, request.zoom_factor);
                    if( bitmaps != null ) {
                        bitmaps.set(0, null);
                        bitmaps.set(1, null);
                    }
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** time for processing first two bitmaps: " + (System.currentTimeMillis() - this_time_s));
                    }

                    for(int i=2;i<request.jpeg_images.size();i++) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "processAvg for image: " + i);

                        this_time_s = System.currentTimeMillis();
                        Bitmap new_bitmap;
                        if( use_smp ) {
                            // check if we already loaded the bitmap
                            if( MyDebug.LOG )
                                Log.d(TAG, "length of bitmaps list: " + bitmaps.size());
                            if( i < bitmaps.size() ) {
                                if( MyDebug.LOG ) {
                                    Log.d(TAG, "already loaded bitmap from previous iteration with SMP");
                                }
                                new_bitmap = bitmaps.get(i);
                            }
                            else {
                                int n_remaining = request.jpeg_images.size() - i;
                                int n_load = Math.min(n_smp_images, n_remaining);
                                if( MyDebug.LOG ) {
                                    Log.d(TAG, "n_remaining: " + n_remaining);
                                    Log.d(TAG, "n_load: " + n_load);
                                }
                                List<byte []> sub_jpeg_list = new ArrayList<>();
                                for(int j=i;j<i+n_load;j++) {
                                    sub_jpeg_list.add(request.jpeg_images.get(j));
                                }
                                List<Bitmap> new_bitmaps = ImageUtils.loadBitmaps(sub_jpeg_list, -1, inSampleSize);
                                bitmaps.addAll(new_bitmaps);
                                if( MyDebug.LOG )
                                    Log.d(TAG, "length of bitmaps list is now: " + bitmaps.size());
                                new_bitmap = bitmaps.get(i);
                            }
                        }
                        else {
                            new_bitmap = ImageUtils.loadBitmap(request.jpeg_images.get(i), false, inSampleSize);
                        }
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "*** time for loading extra bitmap: " + (System.currentTimeMillis() - this_time_s));
                        }
                        avg_factor = (float)i;
                        this_time_s = System.currentTimeMillis();
                        hdrProcessor.updateAvg(avg_data, width, height, new_bitmap, avg_factor, request.iso, request.exposure_time, request.zoom_factor);
                        // updateAvg recycles new_bitmap
                        if( bitmaps != null ) {
                            bitmaps.set(i, null);
                        }
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "*** time for updating extra bitmap: " + (System.currentTimeMillis() - this_time_s));
                        }
                    }

                    this_time_s = System.currentTimeMillis();
                    nr_bitmap = hdrProcessor.avgBrighten(avg_data, width, height, request.iso, request.exposure_time);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** time for brighten: " + (System.currentTimeMillis() - this_time_s));
                    }
                    avg_data.destroy();
                    //noinspection UnusedAssignment
                    avg_data = null;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** total time for saving NR image: " + (System.currentTimeMillis() - time_s));
                    }
                }
                catch(HDRProcessorException e) {
                    MyDebug.logStackTrace(TAG, "HDRProcessorException", e);
                    throw new RuntimeException();
                }
            }

            if( MyDebug.LOG )
                Log.d(TAG, "nr_bitmap: " + nr_bitmap + " is mutable? " + nr_bitmap.isMutable());
            System.gc();
            main_activity.savingImage(false);

            if( MyDebug.LOG )
                Log.d(TAG, "save NR image");
            success = saveSingleImageNow(request, request.jpeg_images.get(0), nr_bitmap, nr_suffix, true, true, true, false);
            if( MyDebug.LOG && !success )
                Log.e(TAG, "saveSingleImageNow failed for nr image");
            nr_bitmap.recycle();
            System.gc();
        }
        else if( request.process_type == Request.ProcessType.HDR ) {
            if( MyDebug.LOG )
                Log.d(TAG, "hdr");
            if( request.jpeg_images.size() != 1 && request.jpeg_images.size() != 3 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "saveImageNow expected either 1 or 3 images for hdr, not " + request.jpeg_images.size());
                // throw runtime exception, as this is a programming error
                throw new RuntimeException();
            }

            long time_s = System.currentTimeMillis();
            if( request.jpeg_images.size() > 1 ) {
                // if there's only 1 image, we're in DRO mode, and shouldn't save the base image
                // note that in earlier Open Camera versions, we used "_EXP" as the suffix. We now use just "_" from 1.42 onwards, so Google
                // Photos will group them together. (Unfortunately using "_EXP_" doesn't work, the images aren't grouped!)
                saveBaseImages(request, "_");
                if( MyDebug.LOG ) {
                    Log.d(TAG, "HDR performance: time after saving base exposures: " + (System.currentTimeMillis() - time_s));
                }
            }

            // note, even if we failed saving some of the expo images, still try to save the HDR image
            if( MyDebug.LOG )
                Log.d(TAG, "create HDR image");
            main_activity.savingImage(true);

            // see documentation for HDRProcessor.processHDR() - because we're using release_bitmaps==true, we need to make sure that
            // the bitmap that will hold the output HDR image is mutable (in case of options like photo stamp)
            // see test testTakePhotoHDRPhotoStamp.
            int base_bitmap = (request.jpeg_images.size()-1)/2;
            if( MyDebug.LOG )
                Log.d(TAG, "base_bitmap: " + base_bitmap);
            List<Bitmap> bitmaps = ImageUtils.loadBitmaps(request.jpeg_images, base_bitmap, 1);
            if( bitmaps == null ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to load bitmaps");
                main_activity.savingImage(false);
                return false;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "HDR performance: time after decompressing base exposures: " + (System.currentTimeMillis() - time_s));
            }

            if( !processHDR(bitmaps, request, time_s) ) {
                main_activity.getPreview().showToast(null, R.string.failed_to_process_hdr);
                main_activity.savingImage(false);
                return false;
            }

            Bitmap hdr_bitmap = bitmaps.get(0);
            if( MyDebug.LOG )
                Log.d(TAG, "hdr_bitmap: " + hdr_bitmap + " is mutable? " + hdr_bitmap.isMutable());
            bitmaps.clear();
            System.gc();
            main_activity.savingImage(false);

            if( MyDebug.LOG )
                Log.d(TAG, "save HDR image");
            int base_image_id = ((request.jpeg_images.size()-1)/2);
            if( MyDebug.LOG )
                Log.d(TAG, "base_image_id: " + base_image_id);
            String suffix = request.jpeg_images.size() == 1 ? "_DRO" : hdr_suffix;
            success = saveSingleImageNow(request, request.jpeg_images.get(base_image_id), hdr_bitmap, suffix, true, true, true, false);
            if( MyDebug.LOG && !success )
                Log.e(TAG, "saveSingleImageNow failed for hdr image");
            if( MyDebug.LOG ) {
                Log.d(TAG, "HDR performance: time after saving HDR image: " + (System.currentTimeMillis() - time_s));
            }
            hdr_bitmap.recycle();
            System.gc();
        }
        else if( request.process_type == Request.ProcessType.PANORAMA ) {
            if( MyDebug.LOG )
                Log.d(TAG, "panorama");

            // save text file with gyro info
            if( !request.image_capture_intent && request.save_base == Request.SaveBase.SAVEBASE_ALL_PLUS_DEBUG ) {
                /*final StringBuilder gyro_text = new StringBuilder();
                gyro_text.append("Panorama gyro debug info\n");
                gyro_text.append("n images: " + request.gyro_rotation_matrix.size() + ":\n");

                float [] inVector = new float[3];
                float [] outVector = new float[3];
                for(int i=0;i<request.gyro_rotation_matrix.size();i++) {
                    gyro_text.append("Image " + i + ":\n");

                    GyroSensor.setVector(inVector, 1.0f, 0.0f, 0.0f); // vector pointing in "right" direction
                    GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
                    gyro_text.append("    X: " + outVector[0] + " , " + outVector[1] + " , " + outVector[2] + "\n");

                    GyroSensor.setVector(inVector, 0.0f, 1.0f, 0.0f); // vector pointing in "up" direction
                    GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
                    gyro_text.append("    Y: " + outVector[0] + " , " + outVector[1] + " , " + outVector[2] + "\n");

                    GyroSensor.setVector(inVector, 0.0f, 0.0f, -1.0f); // vector pointing behind the device's screen
                    GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
                    gyro_text.append("    -Z: " + outVector[0] + " , " + outVector[1] + " , " + outVector[2] + "\n");

                }*/

                try {
                    StringWriter writer = new StringWriter();

                    writeGyroDebugXml(writer, request);

                    StorageUtils storageUtils = main_activity.getStorageUtils();
                    /*File saveFile = null;
                    Uri saveUri = null;
                    if( storageUtils.isUsingSAF() ) {
                        saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_GYRO_INFO, "", "xml", request.current_date);
                    }
                    else {
                        saveFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_GYRO_INFO, "", "xml", request.current_date);
                        if( MyDebug.LOG )
                            Log.d(TAG, "save to: " + saveFile.getAbsolutePath());
                    }*/
                    // We save to the application specific folder so this works on Android 10 with scoped storage, without having to
                    // rewrite the non-SAF codepath to use MediaStore API (which would also have problems that the gyro debug files would
                    // show up in the MediaStore, hence gallery applications!)
                    // We use this for older Android versions for consistency, plus not a bad idea of to have debug files in the application
                    // folder anyway.
                    File saveFile = storageUtils.createOutputMediaFile(main_activity.getExternalFilesDir(null), StorageUtils.MEDIA_TYPE_GYRO_INFO, "", "xml", request.current_date);
                    Uri saveUri = null;
                    if( MyDebug.LOG )
                        Log.d(TAG, "save to: " + saveFile.getAbsolutePath());

                    OutputStream outputStream;
                    if( saveFile != null )
                        outputStream = new FileOutputStream(saveFile);
                    else
                        outputStream = main_activity.getContentResolver().openOutputStream(saveUri);
                    try {
                        //outputStream.write(gyro_text.toString().getBytes());
                        //noinspection CharsetObjectCanBeUsed
                        outputStream.write(writer.toString().getBytes(Charset.forName("UTF-8")));
                    }
                    finally {
                        outputStream.close();
                    }

                    if( saveFile != null ) {
                        storageUtils.broadcastFile(saveFile, false, false, false, false, null);
                    }
                    else {
                        broadcastSAFFile(saveUri, false, false, false);
                    }
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to write gyro text file", e);
                }
            }

            // for now, just save all the images:
            //String suffix = "_";
            //success = saveImages(request, suffix, false, true, true);

            saveBaseImages(request, "_");

            main_activity.savingImage(true);

            long time_s = System.currentTimeMillis();

            if( MyDebug.LOG )
                Log.d(TAG, "panorama_dir_left_to_right: " + request.panorama_dir_left_to_right);
            if( !request.panorama_dir_left_to_right ) {
                Collections.reverse(request.jpeg_images);
                // shouldn't use gyro_rotation_matrix from this point, but keep in sync with jpeg_images just in case
                Collections.reverse(request.gyro_rotation_matrix);
            }

            // need all to be mutable - n.b., in practice setting to -1
            // doesn't cause a problem on some devices e.g. Galaxy S24+ because the bitmaps may be made
            // mutable in rotateForExif, but this can be reproduced on on emulator at least
            List<Bitmap> bitmaps = ImageUtils.loadBitmaps(request.jpeg_images, -2, 1);
            if( bitmaps == null ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to load bitmaps");
                main_activity.savingImage(false);
                return false;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "panorama performance: time after decompressing base exposures: " + (System.currentTimeMillis() - time_s));
            }

            // rotate the bitmaps if necessary for exif tags
            for(int i=0;i<bitmaps.size();i++) {
                Bitmap bitmap = bitmaps.get(i);
                bitmap = ImageUtils.rotateForExif(bitmap, request.jpeg_images.get(0));
                bitmaps.set(i, bitmap);
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "panorama performance: time after rotating for exif: " + (System.currentTimeMillis() - time_s));
            }

            Bitmap panorama;
            try {
                panorama = panoramaProcessor.panorama(bitmaps, MyApplicationInterface.getPanoramaPicsPerScreen(), request.camera_view_angle_y, request.panorama_crop);
            }
            catch(PanoramaProcessorException e) {
                MyDebug.logStackTrace(TAG, "PanoramaProcessorException from panorama", e);
                if( e.getCode() == PanoramaProcessorException.UNEQUAL_SIZES || e.getCode() == PanoramaProcessorException.FAILED_TO_CROP ) {
                    main_activity.getPreview().showToast(null, R.string.failed_to_process_panorama);
                    Log.e(TAG, "panorama failed: " + e.getCode());
                    bitmaps.clear();
                    System.gc();
                    main_activity.savingImage(false);
                    return false;
                }
                else {
                    // throw RuntimeException, as we shouldn't ever get the error INVALID_N_IMAGES, if we do it's a programming error
                    throw new RuntimeException();
                }
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "panorama performance: time after creating panorama image: " + (System.currentTimeMillis() - time_s));
            }
            if( MyDebug.LOG )
                Log.d(TAG, "panorama: " + panorama);
            bitmaps.clear();
            System.gc();

            main_activity.savingImage(false);

            if( MyDebug.LOG )
                Log.d(TAG, "save panorama image");
            success = saveSingleImageNow(request, request.jpeg_images.get(0), panorama, pano_suffix, true, true, true, true);
            if( MyDebug.LOG && !success )
                Log.e(TAG, "saveSingleImageNow failed for panorama image");
            panorama.recycle();
            System.gc();
        }
        else {
            // see note above how we used to use "_EXP" for the suffix for multiple images
            //String suffix = "_EXP";
            String suffix = "_";
            success = saveImages(request, suffix, false, true, true);
        }

        return success;
    }

    /** Alternative to android.util.Range&lt;Integer&gt;, since that is not mocked so can't be used
     *  in unit testing.
     */
    public static class IntRange {
        private final int lower;
        private final int upper;

        public IntRange(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;

            if( lower > upper ) {
                throw new IllegalArgumentException("lower must be <= upper");
            }
        }

        IntRange(Range<Integer> range) {
            this(range.getLower(), range.getUpper());
        }

        boolean contains(int value) {
            return value >= lower && value <= upper;
        }

        int clamp(int value) {
            if( value <= lower )
                return lower;
            else if( value >= upper )
                return upper;
            return value;
        }
    }

    /** Saves all the JPEG images in request.jpeg_images.
     * @param request The request to save.
     * @param suffix If there is more than one image and first_only is false, the i-th image
     *               filename will be appended with (suffix+i).
     * @param first_only If true, only save the first image.
     * @param update_thumbnail Whether to update the thumbnail and show the animation.
     * @param share If true, the median image will be marked as the one to share (for pause preview
     *              option).
     * @return Whether all images were successfully saved.
     */
    private boolean saveImages(Request request, String suffix, boolean first_only, boolean update_thumbnail, boolean share) {
        boolean success = true;
        int mid_image = request.jpeg_images.size()/2;
        for(int i=0;i<request.jpeg_images.size();i++) {
            // note, even if one image fails, we still try saving the other images - might as well give the user as many images as we can...
            byte [] image = request.jpeg_images.get(i);
            boolean multiple_jpegs = request.jpeg_images.size() > 1 && !first_only;
            String filename_suffix = (multiple_jpegs || request.force_suffix) ? suffix + (i + request.suffix_offset) : "";
            if( request.process_type == Request.ProcessType.X_NIGHT ) {
                filename_suffix = "_Night" + filename_suffix;
            }
            boolean share_image = share && (i == mid_image);
            if( !saveSingleImageNow(request, image, null, filename_suffix, update_thumbnail, share_image, false, false) ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "saveSingleImageNow failed for image: " + i);
                success = false;
            }
            if( first_only )
                break; // only requested the first
        }
        return success;
    }

    /** Saves all the images in request.jpeg_images, depending on the save_base option.
     */
    private void saveBaseImages(Request request, String suffix) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveBaseImages");
        if( !request.image_capture_intent && request.save_base != Request.SaveBase.SAVEBASE_NONE ) {
            if( MyDebug.LOG )
                Log.d(TAG, "save base images");

            Request base_request = request;
            if( request.process_type == Request.ProcessType.PANORAMA ) {
                // Important to save base images for panorama in PNG format, to avoid risk of not being able to reproduce the
                // same issue - decompressing JPEGs can vary between devices!
                // Also disable options that don't really make sense for base panorama images.
                base_request = request.copy();
                base_request.image_format = Request.ImageFormat.PNG;
                base_request.preference_stamp = "preference_stamp_no";
                base_request.preference_textstamp = "";
                base_request.do_auto_stabilise = false;
                base_request.mirror = false;
            }
            else if( request.process_type == Request.ProcessType.AVERAGE ) {
                // In case the base image needs to be postprocessed, we still want to save base images for NR at the 100% JPEG quality
                base_request = request.copy();
                base_request.image_quality = 100;
            }
            // don't update the thumbnails, only do this for the final image - so user doesn't think it's complete, click gallery, then wonder why the final image isn't there
            // also don't mark these images as being shared
            saveImages(base_request, suffix, base_request.save_base == Request.SaveBase.SAVEBASE_FIRST, false, false);
            // ignore return of saveImages - as for deciding whether to pause preview or not (which is all we use the success return for), all that matters is whether we saved the final HDR image
        }
    }

    /** Converts from Request.ImageFormat to Bitmap.CompressFormat.
     */
    private static Bitmap.CompressFormat getBitmapCompressFormat(Request.ImageFormat image_format) {
        Bitmap.CompressFormat compress_format;
        switch( image_format ) {
            case WEBP:
                compress_format = Bitmap.CompressFormat.WEBP;
                break;
            case PNG:
                compress_format = Bitmap.CompressFormat.PNG;
                break;
            default:
                compress_format = Bitmap.CompressFormat.JPEG;
                break;
        }
        return compress_format;
    }

    /** May be run in saver thread or picture callback thread (depending on whether running in background).
     *  The requests.images field is ignored, instead we save the supplied data or bitmap.
     *  If bitmap is null, then the supplied jpeg data is saved. If bitmap is non-null, then the bitmap is
     *  saved, but the supplied data is still used to read EXIF data from.
     *  @param update_thumbnail - Whether to update the thumbnail (and show the animation).
     *  @param share_image - Whether this image should be marked as the one to share (if multiple images can
     *  be saved from a single shot (e.g., saving exposure images with HDR).
     *  @param ignore_raw_only - If true, then save even if RAW Only is set (needed for HDR mode
     *                         where we always save the HDR image even though it's a JPEG - the
     *                         RAW preference only affects the base images.
     * @param ignore_exif_orientation - If bitmap is non-null, then set this to true if the bitmap has already
     *                                  been rotated to account for Exif orientation tags in the data.
     */
    @SuppressLint("SimpleDateFormat")
    private boolean saveSingleImageNow(final Request request, byte [] data, Bitmap bitmap, String filename_suffix, boolean update_thumbnail, boolean share_image, boolean ignore_raw_only, boolean ignore_exif_orientation) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveSingleImageNow");

        if( request.type != Request.Type.JPEG ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveImageNow called with non-jpeg request");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }
        else if( data == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveSingleImageNow called with no data");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }
        long time_s = System.currentTimeMillis();

        boolean success = false;
        final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
        boolean raw_only = !ignore_raw_only && applicationInterface.isRawOnly();
        if( MyDebug.LOG )
            Log.d(TAG, "raw_only: " + raw_only);
        StorageUtils storageUtils = main_activity.getStorageUtils();

        String extension;
        switch( request.image_format ) {
            case WEBP:
                extension = "webp";
                break;
            case PNG:
                extension = "png";
                break;
            default:
                extension = "jpg";
                break;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "extension: " + extension);

        main_activity.savingImage(true);

        // If using SAF or image_capture_intent is true, or using scoped storage, only saveUri is non-null
        // Otherwise, only picFile is non-null
        File picFile = null;
        Uri saveUri = null;
        boolean use_media_store = false;
        ContentValues contentValues = null; // used if using scoped storage
        try {
            if( !raw_only ) {
                PostProcessing.PostProcessBitmapResult postProcessBitmapResult = postProcessing.postProcessBitmap(request, data, bitmap, ignore_exif_orientation);
                bitmap = postProcessBitmapResult.bitmap;
            }

            if( raw_only ) {
                // don't save the JPEG
                success = true;
            }
            else if( request.image_capture_intent ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "image_capture_intent");
                if( request.image_capture_intent_uri != null )
                {
                    // Save the bitmap to the specified URI (use a try/catch block)
                    if( MyDebug.LOG )
                        Log.d(TAG, "save to: " + request.image_capture_intent_uri);
                    saveUri = request.image_capture_intent_uri;
                }
                else
                {
                    // If the intent doesn't contain an URI, send the bitmap as a parcel
                    // (it is a good idea to reduce its size to ~50k pixels before)
                    if( MyDebug.LOG )
                        Log.d(TAG, "sent to intent via parcel");
                    if( bitmap == null ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "create bitmap");
                        // bitmap we return doesn't need to be mutable
                        bitmap = ImageUtils.loadBitmapWithRotation(data, false);
                    }
                    if( bitmap != null ) {
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "decoded bitmap size " + width + ", " + height);
                            Log.d(TAG, "bitmap size: " + width*height*4);
                        }
                        final int small_size_c = 128;
                        if( width > small_size_c ) {
                            float scale = ((float)small_size_c)/(float)width;
                            if( MyDebug.LOG )
                                Log.d(TAG, "scale to " + scale);
                            Matrix matrix = new Matrix();
                            matrix.postScale(scale, scale);
                            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                            // careful, as new_bitmap is sometimes not a copy!
                            if( new_bitmap != bitmap ) {
                                bitmap.recycle();
                                bitmap = new_bitmap;
                            }
                        }
                    }
                    if( MyDebug.LOG ) {
                        if( bitmap != null ) {
                            Log.d(TAG, "returned bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
                            Log.d(TAG, "returned bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
                        }
                        else {
                            Log.e(TAG, "no bitmap created");
                        }
                    }
                    if( bitmap != null )
                        main_activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
                    main_activity.finish();
                }
            }
            else if( storageUtils.isUsingSAF() ) {
                saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, extension, request.current_date);
            }
            else if( MainActivity.useScopedStorage() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "use media store");
                use_media_store = true;
                Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                contentValues = new ContentValues();
                String picName = storageUtils.createMediaFilename(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, 0, "." + extension, request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "picName: " + picName);
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, picName);
                String mime_type = storageUtils.getImageMimeType(extension);
                if( MyDebug.LOG )
                    Log.d(TAG, "mime_type: " + mime_type);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, mime_type);
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    String relative_path = storageUtils.getSaveRelativeFolder();
                    if( MyDebug.LOG )
                        Log.d(TAG, "relative_path: " + relative_path);
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, relative_path);
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                // Note, we catch exceptions specific to insert() here and rethrow as IOException,
                // rather than catching below, to avoid catching things too broadly - e.g.,
                // IllegalStateException can also be thrown via "new Canvas" (from
                // postProcessBitmap()) but this is a programming error that we shouldn't catch.
                // Catching too broadly could mean we miss genuine problems that should be fixed.
                try {
                    saveUri = main_activity.getContentResolver().insert(folder, contentValues);
                }
                catch(IllegalArgumentException e) {
                    // can happen for mediastore method if invalid ContentResolver.insert() call
                    MyDebug.logStackTrace(TAG, "IllegalArgumentException inserting to mediastore", e);
                    throw new IOException();
                }
                catch(IllegalStateException e) {
                    // have received Google Play crashes from ContentResolver.insert() call for mediastore method
                    MyDebug.logStackTrace(TAG, "IllegalStateException inserting to mediastore", e);
                    throw new IOException();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "saveUri: " + saveUri);
                if( saveUri == null ) {
                    throw new IOException();
                }
            }
            else {
                picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, extension, request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + picFile.getAbsolutePath());
            }

            if( MyDebug.LOG )
                Log.d(TAG, "saveUri: " + saveUri);

            if( picFile != null || saveUri != null ) {
                OutputStream outputStream;
                if( picFile != null )
                    outputStream = new FileOutputStream(picFile);
                else
                    outputStream = main_activity.getContentResolver().openOutputStream(saveUri);
                try {
                    if( bitmap != null ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "compress bitmap, quality " + request.image_quality);
                        Bitmap.CompressFormat compress_format = getBitmapCompressFormat(request.image_format);
                        if( request.process_type == Request.ProcessType.PANORAMA && compress_format == Bitmap.CompressFormat.JPEG ) {
                            // panorama xmp only supported for JPEG format
                            savePanoramaBitmap(bitmap, compress_format, request.image_quality, request.jpeg_images.size(), outputStream);
                        }
                        else {
                            bitmap.compress(compress_format, request.image_quality, outputStream);
                        }
                    }
                    else {
                        outputStream.write(data);
                    }
                }
                finally {
                    outputStream.close();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "saveImageNow saved photo");
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Save single image performance: time after saving photo: " + (System.currentTimeMillis() - time_s));
                }

                if( saveUri == null ) { // if saveUri is non-null, then we haven't succeeded until we've copied to the saveUri
                    success = true;
                }

                //if( request.image_format == Request.ImageFormat.STD )
                {
                    // handle transferring/setting Exif tags
                    // ExifInterface now supports WebP and PNG
                    if( bitmap != null ) {
                        // need to update EXIF data! (only supported for JPEG image formats)
                        if( MyDebug.LOG )
                            Log.d(TAG, "set Exif tags from data");
                        if( picFile != null ) {
                            ExifHandler.setExifFromData(request, data, picFile);
                        }
                        else {
                            ParcelFileDescriptor parcelFileDescriptor = main_activity.getContentResolver().openFileDescriptor(saveUri, "rw");
                            try {
                                if( parcelFileDescriptor != null ) {
                                    FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                                    ExifHandler.setExifFromData(request, data, fileDescriptor);
                                }
                                else {
                                    Log.e(TAG, "failed to create ParcelFileDescriptor for saveUri: " + saveUri);
                                }
                            }
                            finally {
                                if( parcelFileDescriptor != null ) {
                                    try {
                                        parcelFileDescriptor.close();
                                    }
                                    catch(IOException e) {
                                        MyDebug.logStackTrace(TAG, "fail to close parcelFileDescriptor", e);
                                    }
                                }
                            }
                        }
                    }
                    else {
                        ExifHandler.updateExif(main_activity, request, picFile, saveUri);
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "Save single image performance: time after updateExif: " + (System.currentTimeMillis() - time_s));
                        }
                    }
                }

                if( update_thumbnail ) {
                    // clear just in case we're unable to update this - don't want an out of date cached uri
                    storageUtils.clearLastMediaScanned();
                }

                // Must be done before broadcastFile()
                // see corresponding note in saveImageNowRaw()
                if( raw_only ) {
                    // no saved image to record
                }
                else if( request.image_capture_intent ) {
                    // no need to store as last image
                }
                else if( saveUri == null ) {
                    applicationInterface.addLastImage(picFile, share_image);
                }
                else if( storageUtils.isUsingSAF() ){
                    applicationInterface.addLastImageSAF(saveUri, share_image);
                }
                else if( use_media_store ){
                    applicationInterface.addLastImageMediaStore(saveUri, share_image);
                }

                boolean hasnoexifdatetime = request.remove_device_exif != Request.RemoveDeviceExif.OFF && request.remove_device_exif != Request.RemoveDeviceExif.KEEP_DATETIME;

                if( picFile != null && saveUri == null ) {
                    // broadcast for SAF is done later, when we've actually written out the file
                    storageUtils.broadcastFile(picFile, true, false, update_thumbnail, hasnoexifdatetime, null);
                    main_activity.test_last_saved_image = picFile.getAbsolutePath();
                }

                if( request.image_capture_intent ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "finish activity due to being called from intent");
                    main_activity.setResult(Activity.RESULT_OK);
                    main_activity.finish();
                }

                if( saveUri != null ) {
                    success = true;

                    if( use_media_store ) {
                        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                            contentValues.clear();
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                            main_activity.getContentResolver().update(saveUri, contentValues, null, null);
                        }

                        // no need to broadcast when using mediastore method
                        if( !request.image_capture_intent ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "announce mediastore uri");
                            // in theory this is pointless, as announceUri no longer does anything on Android 7+,
                            // and mediastore method is only used on Android 10+, but keep this just in case
                            // announceUri does something in future
                            storageUtils.announceUri(saveUri, true, false);
                            if( update_thumbnail ) {
                                // we also want to save the uri - we can use the media uri directly, rather than having to scan it
                                storageUtils.setLastMediaScanned(saveUri, false, hasnoexifdatetime, saveUri);
                            }
                        }
                    }
                    else {
                        broadcastSAFFile(saveUri, update_thumbnail, hasnoexifdatetime, request.image_capture_intent);
                    }

                    main_activity.test_last_saved_imageuri = saveUri;
                }
            }
        }
        catch(FileNotFoundException e) {
            MyDebug.logStackTrace(TAG, "file not found", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "I/O error writing file", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        catch(SecurityException e) {
            // received security exception from copyFileToUri()->openOutputStream() from Google Play
            // update: no longer have copyFileToUri() (as no longer use temporary files for SAF), but might as well keep this
            MyDebug.logStackTrace(TAG, "security exception writing file", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }

        // I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
        if( success && main_activity.getPreview().getCameraController() != null && update_thumbnail ) {
            // update thumbnail - this should be done after restarting preview, so that the preview is started asap
            CameraController.Size size = main_activity.getPreview().getCameraController().getPictureSize();
            int ratio = (int) Math.ceil((double) size.width / main_activity.getPreview().getView().getWidth());
            int sample_size = Integer.highestOneBit(ratio);
            sample_size *= request.sample_factor;
            if( MyDebug.LOG ) {
                Log.d(TAG, "    picture width: " + size.width);
                Log.d(TAG, "    preview width: " + main_activity.getPreview().getView().getWidth());
                Log.d(TAG, "    ratio        : " + ratio);
                Log.d(TAG, "    sample_size  : " + sample_size);
            }
            Bitmap thumbnail;
            if( bitmap == null ) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = false;
                options.inSampleSize = sample_size;
                thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "thumbnail width: " + thumbnail.getWidth());
                    Log.d(TAG, "thumbnail height: " + thumbnail.getHeight());
                }
                // now get the rotation from the Exif data
                if( MyDebug.LOG )
                    Log.d(TAG, "rotate thumbnail for exif tags?");
                thumbnail = ImageUtils.rotateForExif(thumbnail, data);
            }
            else {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                Matrix matrix = new Matrix();
                float scale = 1.0f / (float)sample_size;
                matrix.postScale(scale, scale);
                if( MyDebug.LOG )
                    Log.d(TAG, "    scale: " + scale);
                try {
                    thumbnail = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "thumbnail width: " + thumbnail.getWidth());
                        Log.d(TAG, "thumbnail height: " + thumbnail.getHeight());
                    }
                    // don't need to rotate for exif, as we already did that when creating the bitmap
                }
                catch(IllegalArgumentException e) {
                    // received IllegalArgumentException on Google Play from Bitmap.createBitmap; documentation suggests this
                    // means width or height are 0 - but trapping that didn't fix the problem
                    // or "the x, y, width, height values are outside of the dimensions of the source bitmap", but that can't be
                    // true here
                    // crashes seem to all be Android 7.1 or earlier, so maybe this is a bug that's been fixed - but catch it anyway
                    // as it's grown popular
                    MyDebug.logStackTrace(TAG, "can't create thumbnail bitmap due to IllegalArgumentException?!", e);
                    thumbnail = null;
                }
            }
            if( thumbnail == null ) {
                // received crashes on Google Play suggesting that thumbnail could not be created
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to create thumbnail bitmap");
            }
            else {
                final Bitmap thumbnail_f = thumbnail;
                main_activity.runOnUiThread(new Runnable() {
                    public void run() {
                        applicationInterface.updateThumbnail(thumbnail_f, false);
                    }
                });
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Save single image performance: time after creating thumbnail: " + (System.currentTimeMillis() - time_s));
                }
            }
        }

        if( bitmap != null ) {
            bitmap.recycle();
        }

        System.gc();

        main_activity.savingImage(false);

        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: total time: " + (System.currentTimeMillis() - time_s));
        }
        return success;
    }

    private void savePanoramaBitmap(Bitmap bitmap, Bitmap.CompressFormat compress_format, int quality, int n_pics, OutputStream outputStream) throws IOException {
        // need to write to a temporary stream, so we can insert XMP tags
        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
        bitmap.compress(compress_format, quality, jpegStream);
        byte [] jpegData = jpegStream.toByteArray();

        if( jpegData[0] != (byte) 0xFF || jpegData[1] != (byte) 0xD8 ) {
            // invalid jpeg header?! best not to mess with it
            if( MyDebug.LOG )
                Log.d(TAG, "invalid jpeg header, skip adding panorama xmp");
            outputStream.write(jpegData, 0, jpegData.length);
            return;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // see code in MyApplicationInterface.setNextPanoramaPoint()
        float camera_angle_y = main_activity.getPreview().getViewAngleY(false); // angle spanned by one image
        float angle_per_pic = camera_angle_y / MyApplicationInterface.getPanoramaPicsPerScreen(); // extra angle per extra pic
        float total_angle = camera_angle_y + angle_per_pic * (n_pics-1);
        float n_pics_for_360 = 360.0f/total_angle;
        int full_width = (int)(width * n_pics_for_360 + 0.5f);

        //int full_height = (int)(height * n_pics_for_360 * 0.5f + 0.5f);
        //float camera_angle_x = main_activity.getPreview().getViewAngleX(false); // angle spanned by one image
        //int full_height = (int)(height * (180.0f/camera_angle_x) + 0.5f);
        int full_height = full_width/2; // full resolution is 360x180 degrees, and don't want to preserve aspect ratio
        full_height = Math.max(full_height, height); // just in case!

        int cropped_left = (full_width - width)/2;
        int cropped_top = (full_height - height)/2;
        if( MyDebug.LOG ) {
            Log.d(TAG, "camera_angle_y: " + camera_angle_y);
            Log.d(TAG, "angle_per_pic: " + angle_per_pic);
            Log.d(TAG, "total_angle: " + total_angle);
            Log.d(TAG, "n_pics_for_360: " + n_pics_for_360);
            Log.d(TAG, "width: " + width);
            Log.d(TAG, "full_width: " + full_width);
            Log.d(TAG, "height: " + height);
            Log.d(TAG, "full_height: " + full_height);
            Log.d(TAG, "cropped_left: " + cropped_left);
            Log.d(TAG, "cropped_top: " + cropped_top);
        }

        String xmp =
                "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">" +
                        " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
                        "  <rdf:Description xmlns:GPano=\"http://ns.google.com/photos/1.0/panorama/\"" +
                        "    GPano:ProjectionType=\"equirectangular\"" +
                        "    GPano:FullPanoWidthPixels=\"" + full_width + "\"" +
                        "    GPano:FullPanoHeightPixels=\"" + full_height + "\"" +
                        "    GPano:CroppedAreaImageWidthPixels=\"" + width + "\"" +
                        "    GPano:CroppedAreaImageHeightPixels=\"" + height + "\"" +
                        "    GPano:CroppedAreaLeftPixels=\"" + cropped_left + "\"" +
                        "    GPano:CroppedAreaTopPixels=\"" + cropped_top + "\"" +
                        "/>" +
                        " </rdf:RDF>" +
                        "</x:xmpmeta>";

        String xmpPacket = "http://ns.adobe.com/xap/1.0/\u0000" + xmp;

        byte [] xmpBytes = xmpPacket.getBytes(StandardCharsets.UTF_8);
        int segmentLength = xmpBytes.length + 2;

        // jpeg header
        outputStream.write(0xFF);
        outputStream.write(0xD8);

        // XMP segment
        outputStream.write(0xFF);
        outputStream.write(0xE1); // APP1
        outputStream.write((segmentLength >> 8) & 0xFF);
        outputStream.write(segmentLength & 0xFF);
        outputStream.write(xmpBytes);

        // rest of JPEG data (skip original SOI)
        outputStream.write(jpegData, 2, jpegData.length - 2);
    }

    private void broadcastSAFFile(Uri saveUri, boolean set_last_scanned, boolean hasnoexifdatetime, boolean image_capture_intent) {
        if( MyDebug.LOG )
            Log.d(TAG, "broadcastSAFFile");
        StorageUtils storageUtils = main_activity.getStorageUtils();
        storageUtils.broadcastUri(saveUri, true, false, set_last_scanned, hasnoexifdatetime, image_capture_intent);
    }

    /** May be run in saver thread or picture callback thread (depending on whether running in background).
     */
    private boolean saveImageNowRaw(Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveImageNowRaw");

        StorageUtils storageUtils = main_activity.getStorageUtils();
        boolean success = false;

        main_activity.savingImage(true);

        OutputStream output = null;
        RawImage raw_image = request.raw_image;
        try {
            File picFile = null;
            Uri saveUri = null;
            boolean use_media_store = false;
            ContentValues contentValues = null; // used if using scoped storage

            String suffix = "_";
            String filename_suffix = (request.force_suffix) ? suffix + (request.suffix_offset) : "";
            if( storageUtils.isUsingSAF() ) {
                saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, "dng", request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "saveUri: " + saveUri);
                // When using SAF, we don't save to a temp file first (unlike for JPEGs). Firstly we don't need to modify Exif, so don't
                // need a real file; secondly copying to a temp file is much slower for RAW.
            }
            else if( MainActivity.useScopedStorage() ) {
                use_media_store = true;
                Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                contentValues = new ContentValues();
                String picName = storageUtils.createMediaFilename(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, 0, ".dng", request.current_date);
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, picName);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/dng");
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, storageUtils.getSaveRelativeFolder());
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                // Note, we catch exceptions specific to insert() here and rethrow as IOException,
                // rather than catching below, to avoid catching things too broadly.
                // Catching too broadly could mean we miss genuine problems that should be fixed.
                try {
                    saveUri = main_activity.getContentResolver().insert(folder, contentValues);
                }
                catch(IllegalArgumentException e) {
                    // can happen for mediastore method if invalid ContentResolver.insert() call
                    MyDebug.logStackTrace(TAG, "IllegalArgumentException inserting to mediastore", e);
                    throw new IOException();
                }
                catch(IllegalStateException e) {
                    MyDebug.logStackTrace(TAG, "IllegalStateException inserting to mediastore", e);
                    throw new IOException();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "saveUri: " + saveUri);
                if( saveUri == null )
                    throw new IOException();
            }
            else {
                picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, "dng", request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + picFile.getAbsolutePath());
            }

            if( picFile != null ) {
                output = new FileOutputStream(picFile);
            }
            else {
                output = main_activity.getContentResolver().openOutputStream(saveUri);
            }
            raw_image.writeImage(output);
            raw_image.close();
            raw_image = null;
            output.close();
            output = null;
            success = true;

            // set last image for share/trash options for pause preview
            // Must be done before broadcastFile() (because on Android 7+ with non-SAF, we update
            // the LastImage's uri from the MediaScannerConnection.scanFile() callback from
            // StorageUtils.broadcastFile(), which assumes the last image has already been set.
            MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
            boolean raw_only = applicationInterface.isRawOnly();
            if( MyDebug.LOG )
                Log.d(TAG, "raw_only: " + raw_only);
            if( saveUri == null ) {
                applicationInterface.addLastImage(picFile, raw_only);
            }
            else if( storageUtils.isUsingSAF() ){
                applicationInterface.addLastImageSAF(saveUri, raw_only);
            }
            else if( success && use_media_store ){
                applicationInterface.addLastImageMediaStore(saveUri, raw_only);
            }

            // if RAW only, need to update the cached uri
            if( raw_only ) {
                // clear just in case we're unable to update this - don't want an out of date cached uri
                storageUtils.clearLastMediaScanned();
            }

            // n.b., at time of writing, remove_device_exif will always be OFF for RAW, but have added the code for future proofing
            boolean hasnoexifdatetime = request.remove_device_exif != Request.RemoveDeviceExif.OFF && request.remove_device_exif != Request.RemoveDeviceExif.KEEP_DATETIME;

            if( saveUri == null ) {
                storageUtils.broadcastFile(picFile, true, false, raw_only, hasnoexifdatetime, null);
            }
            else if( use_media_store ) {
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    contentValues.clear();
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                    main_activity.getContentResolver().update(saveUri, contentValues, null, null);
                }

                // no need to broadcast when using mediastore method

                // in theory this is pointless, as announceUri no longer does anything on Android 7+,
                // and mediastore method is only used on Android 10+, but keep this just in case
                // announceUri does something in future
                storageUtils.announceUri(saveUri, true, false);

                if( raw_only ) {
                    // we also want to save the uri - we can use the media uri directly, rather than having to scan it
                    storageUtils.setLastMediaScanned(saveUri, true, hasnoexifdatetime, saveUri);
                }
            }
            else {
                storageUtils.broadcastUri(saveUri, true, false, raw_only, hasnoexifdatetime, false);
            }
        }
        catch(FileNotFoundException e) {
            MyDebug.logStackTrace(TAG, "file not found", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo_raw);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "ioexception writing raw image file", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo_raw);
        }
        finally {
            if( output != null ) {
                try {
                    output.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "ioexception closing raw output", e);
                }
            }
            if( raw_image != null ) {
                raw_image.close();
            }
        }

        System.gc();

        main_activity.savingImage(false);

        return success;
    }

    PostProcessing getPostProcessing() {
        return this.postProcessing;
    }

    // for testing:

    HDRProcessor getHDRProcessor() {
        return hdrProcessor;
    }

    public PanoramaProcessor getPanoramaProcessor() {
        return panoramaProcessor;
    }
}

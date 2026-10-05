package net.sourceforge.opencamera.cameracontroller;

import android.graphics.Rect;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.TonemapCurve;
import android.location.Location;
import android.os.Build;
import android.util.Log;
import android.util.Range;

import androidx.exifinterface.media.ExifInterface;

import net.sourceforge.opencamera.MyDebug;

import java.util.Arrays;
import java.util.Locale;

/** Keeps track of the settings (keys) that we wish to set for the various CaptureRequests, along
 *  with methods to actually set these keys.
 */
public class Camera2Settings {
    private static final String TAG = "Camera2Settings";

    private final CameraController2 camera_controller;

    // keys that we need to store, to pass to the stillBuilder, but doesn't need to be passed to previewBuilder (should set sensible defaults)
    int rotation;
    Location location;
    byte jpeg_quality = 90;

    // keys that we have passed to the previewBuilder, that we need to store to also pass to the stillBuilder (should set sensible defaults, or use a has_ boolean if we don't want to set a default)
    int scene_mode = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
    int color_effect = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
    int white_balance = CameraMetadata.CONTROL_AWB_MODE_AUTO;
    private boolean has_default_color_correction;
    private Integer default_color_correction;
    boolean has_antibanding;
    int antibanding = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO;
    boolean has_edge_mode;
    int edge_mode = CameraMetadata.EDGE_MODE_FAST;
    private boolean has_default_edge_mode;
    private Integer default_edge_mode;
    boolean has_noise_reduction_mode;
    int noise_reduction_mode = CameraMetadata.NOISE_REDUCTION_MODE_FAST;
    private boolean has_default_noise_reduction_mode;
    private Integer default_noise_reduction_mode;
    int white_balance_temperature = 5000; // used for white_balance == CONTROL_AWB_MODE_OFF
    String flash_value = "flash_off";
    boolean has_iso;
    //private int ae_mode = CameraMetadata.CONTROL_AE_MODE_ON;
    //private int flash_mode = CameraMetadata.FLASH_MODE_OFF;
    int iso;
    long exposure_time = CameraController.EXPOSURE_TIME_DEFAULT;
    boolean has_aperture;
    float aperture;
    boolean has_control_zoom_ratio; // zoom for Android 11+
    float control_zoom_ratio; // zoom for Android 11+
    Rect scalar_crop_region; // zoom for older Android versions; no need for has_scalar_crop_region, as we can set to null instead
    boolean has_ae_exposure_compensation;
    int ae_exposure_compensation;
    boolean has_af_mode;
    int af_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    float focus_distance; // actual value passed to camera device (set to 0.0 if in infinity mode)
    float focus_distance_manual; // saved setting when in manual mode (so if user switches to infinity mode and back, we'll still remember the manual focus distance)
    boolean ae_lock;
    boolean wb_lock;
    MeteringRectangle[] af_regions; // no need for has_af_regions, as we can set to null instead
    MeteringRectangle [] ae_regions; // no need for has_ae_regions, as we can set to null instead
    boolean has_face_detect_mode;
    int face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
    private Integer default_optical_stabilization;
    boolean video_stabilization;
    CameraController.TonemapProfile tonemap_profile = CameraController.TonemapProfile.TONEMAPPROFILE_OFF;
    float log_profile_strength; // for TONEMAPPROFILE_LOG
    float gamma_profile; // for TONEMAPPROFILE_GAMMA
    private Integer default_tonemap_mode; // since we don't know what a device's tonemap mode is, we save it so we can switch back to it
    Range<Integer> ae_target_fps_range;
    long sensor_frame_duration;

    private final boolean is_samsung;
    private final boolean is_samsung_s7; // Galaxy S7 or Galaxy S7 Edge

    Camera2Settings(CameraController2 camera_controller) {
        this.camera_controller = camera_controller;

        this.is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        String build_model = Build.MODEL.toLowerCase(Locale.US);
        this.is_samsung_s7 = build_model.contains("sm-g93");
        if( MyDebug.LOG ) {
            Log.d(TAG, "is_samsung: " + is_samsung);
            Log.d(TAG, "is_samsung_s7: " + is_samsung_s7);
        }
    }

    int getExifOrientation() {
        int exif_orientation = ExifInterface.ORIENTATION_NORMAL;
        switch( (rotation + 360) % 360 ) {
            case 0:
                exif_orientation = ExifInterface.ORIENTATION_NORMAL;
                break;
            case 90:
                exif_orientation = (camera_controller.getFacing() == CameraController.Facing.FACING_FRONT) ?
                        ExifInterface.ORIENTATION_ROTATE_270 :
                        ExifInterface.ORIENTATION_ROTATE_90;
                break;
            case 180:
                exif_orientation = ExifInterface.ORIENTATION_ROTATE_180;
                break;
            case 270:
                exif_orientation = (camera_controller.getFacing() == CameraController.Facing.FACING_FRONT) ?
                        ExifInterface.ORIENTATION_ROTATE_90 :
                        ExifInterface.ORIENTATION_ROTATE_270;
                break;
            default:
                // leave exif_orientation unchanged
                if( MyDebug.LOG )
                    Log.e(TAG, "unexpected rotation: " + rotation);
                break;
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "rotation: " + rotation);
            Log.d(TAG, "exif_orientation: " + exif_orientation);
        }
        return exif_orientation;
    }

    void setupBuilder(CaptureRequest.Builder builder, boolean is_still) {
        //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        //builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

        if( !camera_controller.isExtensionSession() ) {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        }

        setSceneMode(builder);
        setColorEffect(builder);
        setWhiteBalance(builder);
        setAntiBanding(builder);
        setAEMode(builder, is_still);
        setControlZoomRatio(builder);
        setCropRegion(builder);
        setExposureCompensation(builder);
        setFocusMode(builder);
        setFocusDistance(builder);
        setAutoExposureLock(builder);
        setAutoWhiteBalanceLock(builder);
        setAFRegions(builder);
        setAERegions(builder);
        setFaceDetectMode(builder);
        setRawMode(builder);
        setStabilization(builder);
        setTonemapProfile(builder);

        if( is_still ) {
            if( location != null && !camera_controller.isExtensionSession() ) {
                // JPEG_GPS_LOCATION not supported for camera extensions, so instead this must
                // be set by the caller when receiving the image data (see ImageSaver.modifyExif(),
                // where we do this using ExifInterface.setGpsInfo()).
                builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
            }
            builder.set(CaptureRequest.JPEG_ORIENTATION, rotation);
            builder.set(CaptureRequest.JPEG_QUALITY, jpeg_quality);
        }

        setEdgeMode(builder);
        setNoiseReductionMode(builder);

            /*builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
            builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF);
            builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF);*/

            /*builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_GAMMA_VALUE);
                builder.set(CaptureRequest.TONEMAP_GAMMA, 5.0f);
            }*/
            /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 0);
            }*/
            /*builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF);
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF);
            builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF);*/
            /*if( MyDebug.LOG ) {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
                TonemapCurve original_curve = builder.get(CaptureRequest.TONEMAP_CURVE);
                for(int c=0;c<3;c++) {
                    Log.d(TAG, "color c = " + c);
                    for(int i=0;i<original_curve.getPointCount(c);i++) {
                        PointF point = original_curve.getPoint(c, i);
                        Log.d(TAG, "    i = " + i);
                        Log.d(TAG, "        in: " + point.x);
                        Log.d(TAG, "        out: " + point.y);
                    }
                }
            }*/
            /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
                builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB);
            }*/

        if( MyDebug.LOG ) {
            if( is_still ) {
                Integer nr_mode = builder.get(CaptureRequest.NOISE_REDUCTION_MODE);
                Log.d(TAG, "nr_mode: " + (nr_mode==null ? "null" : nr_mode));
                Integer edge_mode = builder.get(CaptureRequest.EDGE_MODE);
                Log.d(TAG, "edge_mode: " + (edge_mode==null ? "null" : edge_mode));
                Integer control_mode = builder.get(CaptureRequest.CONTROL_MODE);
                Log.d(TAG, "control_mode: " + (control_mode==null ? "null" : control_mode));
                Integer scene_mode = builder.get(CaptureRequest.CONTROL_SCENE_MODE);
                Log.d(TAG, "scene_mode: " + (scene_mode==null ? "null" : scene_mode));
                Integer cc_mode = builder.get(CaptureRequest.COLOR_CORRECTION_MODE);
                Log.d(TAG, "cc_mode: " + (cc_mode==null ? "null" : cc_mode));
                Integer cca_mode = builder.get(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE);
                Log.d(TAG, "cca_mode: " + (cc_mode==null ? "null" : cca_mode));
                    /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                        Integer raw_sensitivity_boost = builder.get(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST);
                        Log.d(TAG, "raw_sensitivity_boost: " + (raw_sensitivity_boost==null ? "null" : raw_sensitivity_boost));
                    }*/
            }
            //Integer ois_mode = builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE);
            //Log.d(TAG, "ois_mode: " + (ois_mode==null ? "null" : ois_mode));
        }
    }

    boolean setSceneMode(CaptureRequest.Builder builder) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setSceneMode");
            Log.d(TAG, "builder: " + builder);
            Log.d(TAG, "has_face_detect_mode: " + has_face_detect_mode);
        }

        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
            return false;
        }

        Integer current_mode = builder.get(CaptureRequest.CONTROL_MODE);
        Integer current_scene_mode = builder.get(CaptureRequest.CONTROL_SCENE_MODE);
        if( MyDebug.LOG )
            Log.d(TAG, "current_scene_mode: " + current_scene_mode);
        if( has_face_detect_mode ) {
            // face detection mode overrides scene mode
            if( MyDebug.LOG )
                Log.d(TAG, "setting scene mode for face detection");
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);
            if( current_mode == null || current_mode != CameraMetadata.CONTROL_MODE_USE_SCENE_MODE || current_scene_mode == null || current_scene_mode != CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY )
                return true;
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "setting scene mode: " + scene_mode);
            // note we set CONTROL_MODE_AUTO even if using manual exposure, focus or awb, as we set that separately via
            // CONTROL_AE_MODE_OFF etc
            int new_mode = scene_mode == CameraMetadata.CONTROL_SCENE_MODE_DISABLED ?
                    CameraMetadata.CONTROL_MODE_AUTO : CameraMetadata.CONTROL_MODE_USE_SCENE_MODE;
            builder.set(CaptureRequest.CONTROL_MODE, new_mode);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, scene_mode);
            if( current_mode == null || current_mode != new_mode || current_scene_mode == null || current_scene_mode != scene_mode )
                return true;
        }
        return false;
    }

    boolean setColorEffect(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
            /*else if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null && color_effect == CameraMetadata.CONTROL_EFFECT_MODE_OFF ) {
                // can leave off
            }*/
        else if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null || builder.get(CaptureRequest.CONTROL_EFFECT_MODE) != color_effect ) {
            if( MyDebug.LOG )
                Log.d(TAG, "setting color effect: " + color_effect);
            builder.set(CaptureRequest.CONTROL_EFFECT_MODE, color_effect);
            return true;
        }
        return false;
    }

    boolean setWhiteBalance(CaptureRequest.Builder builder) {
        boolean changed = false;
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
            /*else if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null && white_balance == CameraMetadata.CONTROL_AWB_MODE_AUTO ) {
                // can leave off
            }*/
        else if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null || builder.get(CaptureRequest.CONTROL_AWB_MODE) != white_balance ) {
            if( MyDebug.LOG )
                Log.d(TAG, "setting white balance: " + white_balance);

            // if we'd set COLOR_CORRECTION_MODE to non-default, now put it back to default
            if( has_default_color_correction ) {
                if( builder.get(CaptureRequest.COLOR_CORRECTION_MODE) != null && !builder.get(CaptureRequest.COLOR_CORRECTION_MODE).equals(default_color_correction) ) {
                    builder.set(CaptureRequest.COLOR_CORRECTION_MODE, default_color_correction);
                }
                has_default_color_correction = false; // set to false, as only need to set COLOR_CORRECTION_MODE back to default when changing from manual back to non-manual white balance
            }

            builder.set(CaptureRequest.CONTROL_AWB_MODE, white_balance);
            changed = true;
        }
        if( white_balance == CameraMetadata.CONTROL_AWB_MODE_OFF ) {
            if( MyDebug.LOG )
                Log.d(TAG, "setting white balance temperature: " + white_balance_temperature);
            // manual white balance

            if( !has_default_color_correction ) {
                // save the default COLOR_CORRECTION_MODE
                has_default_color_correction = true;
                default_color_correction = builder.get(CaptureRequest.COLOR_CORRECTION_MODE);
                if( MyDebug.LOG )
                    Log.d(TAG, "default_color_correction: " + default_color_correction);
            }

            RggbChannelVector rggbChannelVector = CameraController2.convertTemperatureToRggbVector(white_balance_temperature);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
            if( MyDebug.LOG ) {
                Log.d(TAG, "original color_correction_transform: " + builder.get(CaptureRequest.COLOR_CORRECTION_TRANSFORM));
            }
            // need to set COLOR_CORRECTION_TRANSFORM on some devices (e.g. Pixel 6 Pro) as they don't have it set by default
            ColorSpaceTransform color_space_transform = new ColorSpaceTransform(new int[]
                    {
                            1, 1, 0, 1, 0, 1,
                            0, 1, 1, 1, 0, 1,
                            0, 1, 0, 1, 1, 1
                    });
            builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, color_space_transform);
            changed = true;
        }
        return changed;
    }

    boolean setAntiBanding(CaptureRequest.Builder builder) {
        boolean changed = false;
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( has_antibanding ) {
            if( builder.get(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE) == null || builder.get(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE) != antibanding ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting antibanding: " + antibanding);
                builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, antibanding);
                changed = true;
            }
        }
        return changed;
    }

    boolean setEdgeMode(CaptureRequest.Builder builder) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setEdgeMode");
            Log.d(TAG, "has_default_edge_mode: " + has_default_edge_mode);
            Log.d(TAG, "default_edge_mode: " + default_edge_mode);
        }
        boolean changed = false;
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( has_edge_mode ) {
            if( !has_default_edge_mode ) {
                // save the default_edge_mode edge_mode
                has_default_edge_mode = true;
                default_edge_mode = builder.get(CaptureRequest.EDGE_MODE);
                if( MyDebug.LOG )
                    Log.d(TAG, "default_edge_mode: " + default_edge_mode);
            }
            if( builder.get(CaptureRequest.EDGE_MODE) == null || builder.get(CaptureRequest.EDGE_MODE) != edge_mode ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting edge_mode: " + edge_mode);
                builder.set(CaptureRequest.EDGE_MODE, edge_mode);
                changed = true;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "edge_mode was already set: " + edge_mode);
            }
        }
        else if( is_samsung_s7 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set EDGE_MODE_OFF");
            // see https://sourceforge.net/p/opencamera/discussion/general/thread/48bd836b/ ,
            // https://stackoverflow.com/questions/36028273/android-camera-api-glossy-effect-on-galaxy-s7
            // need EDGE_MODE_OFF to avoid a "glow" effect
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
        }
        else if( has_default_edge_mode ) {
            if( builder.get(CaptureRequest.EDGE_MODE) != null && !builder.get(CaptureRequest.EDGE_MODE).equals(default_edge_mode) ) {
                builder.set(CaptureRequest.EDGE_MODE, default_edge_mode);
                changed = true;
            }
        }
        return changed;
    }

    boolean setNoiseReductionMode(CaptureRequest.Builder builder) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setNoiseReductionMode");
            Log.d(TAG, "has_default_noise_reduction_mode: " + has_default_noise_reduction_mode);
            Log.d(TAG, "default_noise_reduction_mode: " + default_noise_reduction_mode);
        }
        boolean changed = false;
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( has_noise_reduction_mode ) {
            if( !has_default_noise_reduction_mode ) {
                // save the default_noise_reduction_mode noise_reduction_mode
                has_default_noise_reduction_mode = true;
                default_noise_reduction_mode = builder.get(CaptureRequest.NOISE_REDUCTION_MODE);
                if( MyDebug.LOG )
                    Log.d(TAG, "default_noise_reduction_mode: " + default_noise_reduction_mode);
            }
            if( builder.get(CaptureRequest.NOISE_REDUCTION_MODE) == null || builder.get(CaptureRequest.NOISE_REDUCTION_MODE) != noise_reduction_mode ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting noise_reduction_mode: " + noise_reduction_mode);
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noise_reduction_mode);
                changed = true;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "noise_reduction_mode was already set: " + noise_reduction_mode);
            }
        }
        else if( is_samsung_s7 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set NOISE_REDUCTION_MODE_OFF");
            // see https://sourceforge.net/p/opencamera/discussion/general/thread/48bd836b/ ,
            // https://stackoverflow.com/questions/36028273/android-camera-api-glossy-effect-on-galaxy-s7
            // need NOISE_REDUCTION_MODE_OFF to avoid excessive blurring
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
        }
        else if( has_default_noise_reduction_mode ) {
            if( builder.get(CaptureRequest.NOISE_REDUCTION_MODE) != null && !builder.get(CaptureRequest.NOISE_REDUCTION_MODE).equals(default_noise_reduction_mode)) {
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE, default_noise_reduction_mode);
                changed = true;
            }
        }
        return changed;
    }

    boolean setAperture(CaptureRequest.Builder builder) {
        if( MyDebug.LOG )
            Log.d(TAG, "setAperture");
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( has_aperture ) {
            if( MyDebug.LOG )
                Log.d(TAG, "    aperture: " + aperture);
            builder.set(CaptureRequest.LENS_APERTURE, aperture);
            return true;
        }
        // don't set at all if has_aperture==false
        return false;
    }

    @SuppressWarnings("SameReturnValue")
    boolean setAEMode(CaptureRequest.Builder builder, boolean is_still) {
        if( MyDebug.LOG )
            Log.d(TAG, "setAEMode");

        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
                /*
                // except for low light boost for night mode, if supported
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && camera_extension == CameraExtensionCharacteristics.EXTENSION_NIGHT && supports_low_light_boost && !is_still ) {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY);
                    return true;
                }*/
            return false;
        }

        if( has_iso ) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "manual mode");
                Log.d(TAG, "iso: " + iso);
                Log.d(TAG, "exposure_time: " + exposure_time);
            }
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
            long actual_exposure_time = exposure_time;
            if( !is_still ) {
                // if this isn't for still capture, have a max exposure time of 1/12s
                actual_exposure_time = Math.min(exposure_time, CameraController2.max_preview_exposure_time_c);
                if( MyDebug.LOG )
                    Log.d(TAG, "actually using exposure_time of: " + actual_exposure_time);
            }
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, actual_exposure_time);
            if (sensor_frame_duration > 0) {
                builder.set(CaptureRequest.SENSOR_FRAME_DURATION, sensor_frame_duration);
            }
            //builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L);
            //builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 0L);
            // only need to account for FLASH_MODE_TORCH, otherwise we use fake flash mode for manual ISO
            if( flash_value.equals("flash_torch") ) {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            }
            else {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
            }
        }
        else {
            if( MyDebug.LOG ) {
                Log.d(TAG, "auto mode");
                Log.d(TAG, "flash_value: " + flash_value);
            }
            if( ae_target_fps_range != null ) {
                Log.d(TAG, "set ae_target_fps_range: " + ae_target_fps_range);
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, ae_target_fps_range);
            }

            // prefer to set flash via the ae mode (otherwise get even worse results), except for torch which we can't
            switch(flash_value) {
                case "flash_off":
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    break;
                case "flash_auto":
                    // note we set this even in fake flash mode (where we manually turn torch on and off to simulate flash) so we
                    // can read the FLASH_REQUIRED state to determine if flash is required
                    /*if( use_fake_precapture || camera_controller.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else*/
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    break;
                case "flash_on":
                    // see note above for "flash_auto" for why we set this even fake flash mode - arguably we don't need to know
                    // about FLASH_REQUIRED in flash_on mode, but we set it for consistency...
                    /*if( use_fake_precapture || camera_controller.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else*/
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    break;
                case "flash_torch":
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    break;
                case "flash_red_eye":
                    // not supported for expo bracketing or burst
                    if( camera_controller.getBurstType() != CameraController.BurstType.BURSTTYPE_NONE )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    break;
                case "flash_frontscreen_auto":
                case "flash_frontscreen_on":
                case "flash_frontscreen_torch":
                    //noinspection DuplicateBranchesInSwitch
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    break;
            }
        }
        return true;
    }

    void setControlZoomRatio(CaptureRequest.Builder builder) {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && has_control_zoom_ratio ) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, control_zoom_ratio);
        }
    }

    void setCropRegion(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( scalar_crop_region != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.R ) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, scalar_crop_region);
        }
    }

    boolean setExposureCompensation(CaptureRequest.Builder builder) {
        if( !has_ae_exposure_compensation )
            return false;
        if( has_iso ) {
            if( MyDebug.LOG )
                Log.d(TAG, "don't set exposure compensation in manual iso mode");
            return false;
        }
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
            return false;
        }
        if( builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null || ae_exposure_compensation != builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "change exposure to " + ae_exposure_compensation);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae_exposure_compensation);
            return true;
        }
        return false;
    }

    void setFocusMode(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( has_af_mode ) {
            if( MyDebug.LOG )
                Log.d(TAG, "change af mode to " + af_mode);
            builder.set(CaptureRequest.CONTROL_AF_MODE, af_mode);
        }
        else {
            if( MyDebug.LOG ) {
                Log.d(TAG, "af mode left at " + builder.get(CaptureRequest.CONTROL_AF_MODE));
            }
        }
    }

    void setFocusDistance(CaptureRequest.Builder builder) {
        if( MyDebug.LOG )
            Log.d(TAG, "change focus distance to " + focus_distance);
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else {
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_distance);
        }
    }

    void setAutoExposureLock(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, ae_lock);
        }
    }

    void setAutoWhiteBalanceLock(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else {
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, wb_lock);
        }
    }

    void setAFRegions(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( af_regions != null && camera_controller.supportsFocusRegions() ) {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, af_regions);
        }
    }

    void setAERegions(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( ae_regions != null && camera_controller.supportsMetering() ) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, ae_regions);
        }
    }

    void setFaceDetectMode(CaptureRequest.Builder builder) {
        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( has_face_detect_mode )
            builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, face_detect_mode);
        else
            builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
    }

    private void setRawMode(CaptureRequest.Builder builder) {
        // DngCreator says "For best quality DNG files, it is strongly recommended that lens shading map output is enabled if supported"
        // docs also say "ON is always supported on devices with the RAW capability", so we don't check for STATISTICS_LENS_SHADING_MAP_MODE_ON being available
        if( camera_controller.isWantRaw() && !camera_controller.getPreviewIsVideoMode() ) {
            builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
        }
    }

    void setStabilization(CaptureRequest.Builder builder) {
        if( MyDebug.LOG )
            Log.d(TAG, "setStabilization: " + video_stabilization);

        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
            return;
        }

        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, video_stabilization ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        if( camera_controller.supportsOpticalStabilization() ) {
            if( video_stabilization ) {
                // should also disable OIS
                if( default_optical_stabilization == null ) {
                    // save the default optical_stabilization
                    default_optical_stabilization = builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE);
                    if( MyDebug.LOG )
                        Log.d(TAG, "default_optical_stabilization: " + default_optical_stabilization);
                }
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            }
            else if( default_optical_stabilization != null ) {
                if( builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE) != null && !builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE).equals(default_optical_stabilization) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set optical stabilization back to: " + default_optical_stabilization);
                    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, default_optical_stabilization);
                }
            }
        }
    }

    private float getLogProfile(float in) {
        //final float black_level = 4.0f/255.0f;
        //final float power = 1.0f/2.2f;
        final float log_A = log_profile_strength;
            /*float out;
            if( in <= black_level ) {
                out = in;
            }
            else {
                float in_m = (in - black_level) / (1.0f - black_level);
                out = (float) (Math.log1p(log_A * in_m) / Math.log1p(log_A));
                out = black_level + (1.0f - black_level)*out;
            }*/
        float out = (float) (Math.log1p(log_A * in) / Math.log1p(log_A));

        // apply gamma
        // update: no longer need to do this with improvements made in 1.48 onwards
        //out = (float)Math.pow(out, power);
        //out = Math.max(out, 0.5f);

        return out;
    }

    private float getGammaProfile(float in) {
        return (float)Math.pow(in, 1.0f/gamma_profile);
    }

    void setTonemapProfile(CaptureRequest.Builder builder) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setTonemapProfile");
            Log.d(TAG, "tonemap_profile: " + tonemap_profile);
            Log.d(TAG, "log_profile_strength: " + log_profile_strength);
            Log.d(TAG, "gamma_profile: " + gamma_profile);
            Log.d(TAG, "default_tonemap_mode: " + default_tonemap_mode);
        }
        boolean have_tonemap_profile = tonemap_profile != CameraController.TonemapProfile.TONEMAPPROFILE_OFF;
        if( tonemap_profile == CameraController.TonemapProfile.TONEMAPPROFILE_LOG && log_profile_strength == 0.0f )
            have_tonemap_profile = false;
        else if( tonemap_profile == CameraController.TonemapProfile.TONEMAPPROFILE_GAMMA && gamma_profile == 0.0f )
            have_tonemap_profile = false;

        // to use test_new, also need to uncomment the test code in setFocusValue() to call setTonemapProfile()
        //boolean test_new = this.af_mode == CaptureRequest.CONTROL_AF_MODE_AUTO; // testing

        //if( test_new )
        //    have_tonemap_profile = false;

        if( camera_controller.isExtensionSession() ) {
            // don't set for extensions
        }
        else if( have_tonemap_profile ) {
            if( default_tonemap_mode == null ) {
                // save the default tonemap_mode
                default_tonemap_mode = builder.get(CaptureRequest.TONEMAP_MODE);
                if( MyDebug.LOG )
                    Log.d(TAG, "default_tonemap_mode: " + default_tonemap_mode);
            }

            final boolean use_preset_curve = camera_controller.supportsTonemapPresetCurve();
            //final boolean use_preset_curve = false; // test
            if( use_preset_curve && tonemap_profile == CameraController.TonemapProfile.TONEMAPPROFILE_REC709 /*&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.M */) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set TONEMAP_PRESET_CURVE_REC709");
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
                builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_REC709);
            }
            else if( use_preset_curve && tonemap_profile == CameraController.TonemapProfile.TONEMAPPROFILE_SRGB /*&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.M*/ ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set TONEMAP_PRESET_CURVE_SRGB");
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
                builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB);
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "handle via TONEMAP_MODE_CONTRAST_CURVE / TONEMAP_CURVE");
                float [] values = null;
                switch( tonemap_profile ) {
                    case TONEMAPPROFILE_REC709:
                        // y = 4.5x if x < 0.018, else y = 1.099*x^0.45 - 0.099
                        float [] x_values = new float[] {
                                0.0000f, 0.0667f, 0.1333f, 0.2000f,
                                0.2667f, 0.3333f, 0.4000f, 0.4667f,
                                0.5333f, 0.6000f, 0.6667f, 0.7333f,
                                0.8000f, 0.8667f, 0.9333f, 1.0000f
                        };
                        values = new float[2*x_values.length];
                        int c = 0;
                        for(float x_value : x_values) {
                            float out;
                            if( x_value < 0.018f ) {
                                out = 4.5f * x_value;
                            }
                            else {
                                out = (float)(1.099*Math.pow(x_value, 0.45) - 0.099);
                            }
                            values[c++] = x_value;
                            values[c++] = out;
                        }
                        break;
                    case TONEMAPPROFILE_SRGB:
                        values = new float [] {
                                0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                                0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
                                0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
                                0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f
                        };
                        break;
                    case TONEMAPPROFILE_LOG:
                    case TONEMAPPROFILE_GAMMA:
                    {
                        // better to use uniformly spaced values, otherwise we get a weird looking effect - this can be
                        // seen most prominently when using gamma 1.0f, which should look linear (and hence be independent
                        // of the x values we use)
                        // can be reproduced on at least OnePlus 3T and Galaxy S10e (although the exact behaviour of the
                        // poor results is different on those devices)

                        // unfortunately odd bug on Samsung devices (at least S7 and S10e) where if more than 32 control points,
                        // the maximum brightness value is reduced (can best be seen with 64 points, and using gamma==1.0)
                        // note that Samsung devices also need at least 16 control points - or in some cases 32, see comments for
                        // CameraController2.enforceMinTonemapCurvePoints().
                        // 32 is better than 16 anyway, as better to have more points for finer curve where possible.
                        int n_values = is_samsung ? 32 : CameraController2.tonemap_log_max_curve_points_c;
                        //int n_values = test_new ? 32 : 128;
                        //int n_values = 32;
                        if( MyDebug.LOG )
                            Log.d(TAG, "n_values: " + n_values);

                        values = new float [2*n_values];
                        for(int i=0;i<n_values;i++) {
                            float in = ((float)i) / (n_values-1.0f);
                            float out = (tonemap_profile== CameraController.TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                            values[2*i] = in;
                            values[2*i+1] = out;
                        }
                    }

                        /*if( test_new ) {
                            // if changing this, make sure we don't exceed tonemap_log_max_curve_points_c
                            // we want:
                            // 0-15: step 1 (16 values)
                            // 16-47: step 2 (16 values)
                            // 48-111: step 4 (16 values)
                            // 112-231 : step 8 (15 values)
                            // 232-255: step 24 (1 value)
                            int step = 1, c = 0;
                            //int step = 4, c = 0;
                            //int step = test_new ? 4 : 1, c = 0;
                            values = new float[2*tonemap_log_max_curve_points_c];
                            for(int i=0;i<232;i+=step) {
                                float in = ((float)i) / 255.0f;
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                if( tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG )
                                    out = (float)Math.pow(out, 1.0f/2.2f);
                                values[c++] = in;
                                values[c++] = out;
                                if( (c/2) % 16 == 0 ) {
                                    step *= 2;
                                }
                            }
                            values[c++] = 1.0f;
                            float last_out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(1.0f) : getGammaProfile(1.0f);
                            if( tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG )
                                last_out = (float)Math.pow(last_out, 1.0f/2.2f);
                            values[c++] = last_out;
                            values = Arrays.copyOfRange(values,0,c);
                        }*/
                        /*if( test_new )
                        {
                            // x values are ranged 0 to 255
                            float [] x_values = new float[] {
                                    0.0f, 4.0f, 8.0f, 12.0f, 16.0f, 20.0f, 24.0f, 28.0f,
                                    //0.0f, 8.0f, 16.0f, 24.0f,
                                    32.0f, 40.0f, 48.0f, 56.0f,
                                    64.0f, 72.0f, 80.0f, 88.0f,
                                    96.0f, 104.0f, 112.0f, 120.0f,
                                    128.0f, 136.0f, 144.0f, 152.0f,
                                    160.0f, 168.0f, 176.0f, 184.0f,
                                    192.0f, 200.0f, 208.0f, 216.0f,
                                    224.0f, 232.0f, 240.0f, 248.0f,
                                    255.0f
                            };
                            values = new float[2*x_values.length];
                            c = 0;
                            for(float x_value : x_values) {
                                float in = x_value / 255.0f;
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                values[c++] = in;
                                values[c++] = out;
                            }
                        }*/
                        /*if( test_new )
                        {
                            values = new float [2*256];
                            step = 8;
                            c = 0;
                            for(int i=0;i<254;i+=step) {
                                float in = ((float)i) / 255.0f;
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                values[c++] = in;
                                values[c++] = out;
                            }
                            values[c++] = 1.0f;
                            values[c++] = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(1.0f) : getGammaProfile(1.0f);
                            values = Arrays.copyOfRange(values,0,c);
                        }*/
                    if( MyDebug.LOG ) {
                        int n_values = values.length/2;
                        for(int i=0;i<n_values;i++) {
                            float in = values[2*i];
                            float out = values[2*i+1];
                            Log.d(TAG, "i = " + i);
                            //Log.d(TAG, "    in: " + (int)(in*255.0f+0.5f));
                            //Log.d(TAG, "    out: " + (int)(out*255.0f+0.5f));
                            Log.d(TAG, "    in: " + (in*255.0f));
                            Log.d(TAG, "    out: " + (out*255.0f));
                        }
                    }
                    break;
                    case TONEMAPPROFILE_JTVIDEO:
                        values = camera_controller.jtvideo_values;
                        if( MyDebug.LOG )
                            Log.d(TAG, "setting JTVideo profile");
                        break;
                    case TONEMAPPROFILE_JTLOG:
                        values = camera_controller.jtlog_values;
                        if( MyDebug.LOG )
                            Log.d(TAG, "setting JTLog profile");
                        break;
                    case TONEMAPPROFILE_JTLOG2:
                        values = camera_controller.jtlog2_values;
                        if( MyDebug.LOG )
                            Log.d(TAG, "setting JTLog2 profile");
                        break;
                }

                // sRGB:
                    /*values = new float []{0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                            0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
                            0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
                            0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f};*/
                    /*values = new float []{0.0000f, 0.0000f, 0.05f, 0.3f, 0.1f, 0.4f, 0.2000f, 0.4845f,
                            0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f,
                            0.5f, 0.78f, 1.0000f, 1.0000f};*/
                    /*values = new float []{0.0f, 0.0f, 0.05f, 0.4f, 0.1f, 0.54f, 0.2f, 0.6f, 0.3f, 0.65f, 0.4f, 0.7f,
                            0.5f, 0.78f, 1.0f, 1.0f};*/
                    /*values = new float[]{0.0f, 0.0f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                            1.0f, 1.0f};*/
                //values = new float []{0.0f, 0.5f, 0.05f, 0.6f, 0.1f, 0.7f, 0.2f, 0.8f, 0.5f, 0.9f, 1.0f, 1.0f};
                    /*values = new float []{0.0f, 0.0f,
                            0.05f, 0.05f,
                            0.1f, 0.1f,
                            0.15f, 0.15f,
                            0.2f, 0.2f,
                            0.25f, 0.25f,
                            0.3f, 0.3f,
                            0.35f, 0.35f,
                            0.4f, 0.4f,
                            0.5f, 0.5f,
                            0.6f, 0.6f,
                            0.7f, 0.7f,
                            0.8f, 0.8f,
                            0.9f, 0.9f,
                            0.95f, 0.95f,
                            1.0f, 1.0f};*/
                //values = enforceMinTonemapCurvePoints(new float[]{0.0f, 0.0f, 1.0f, 1.0f});
                //values = enforceMinTonemapCurvePoints(values);

                if( MyDebug.LOG  )
                    Log.d(TAG, "values: " + Arrays.toString(values));
                if( values != null ) {
                    builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE);
                    TonemapCurve tonemap_curve = new TonemapCurve(values, values, values);
                    builder.set(CaptureRequest.TONEMAP_CURVE, tonemap_curve);
                    camera_controller.test_used_tonemap_curve = true;
                }
                else {
                    Log.e(TAG, "unknown log type: " + tonemap_profile);
                }
            }
        }
        else if( default_tonemap_mode != null ) {
            builder.set(CaptureRequest.TONEMAP_MODE, default_tonemap_mode);
        }
    }

    // n.b., if we add more methods, remember to update setupBuilder() above!
}

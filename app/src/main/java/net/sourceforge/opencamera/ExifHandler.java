package net.sourceforge.opencamera;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Methods related to handling exif tags.
 */
public class ExifHandler extends Thread {
    private static final String TAG = "ExifHandler";

    /** Transfers device exif info. Should only be called if request.remove_device_exif == Request.RemoveDeviceExif.OFF.
     */
    private static void transferDeviceExif(ExifInterface exif, ExifInterface exif_new) {
        if( MyDebug.LOG )
            Log.d(TAG, "transferDeviceExif");

        if( MyDebug.LOG )
            Log.d(TAG, "read back EXIF data");

        String exif_aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER); // previously TAG_APERTURE
        String exif_exposure_time = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
        String exif_flash = exif.getAttribute(ExifInterface.TAG_FLASH);
        String exif_focal_length = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
        // leave TAG_IMAGE_WIDTH/TAG_IMAGE_LENGTH, as this may have changed!
        //noinspection deprecation
        String exif_iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS); // previously TAG_ISO
        String exif_make = exif.getAttribute(ExifInterface.TAG_MAKE);
        String exif_model = exif.getAttribute(ExifInterface.TAG_MODEL);
        // leave orientation - since we rotate bitmaps to account for orientation, we don't want to write it to the saved image!
        String exif_white_balance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);

        String exif_aperture_value;
        String exif_brightness_value;
        String exif_cfa_pattern;
        String exif_color_space;
        String exif_components_configuration;
        String exif_compressed_bits_per_pixel;
        String exif_compression;
        String exif_contrast;
        String exif_device_setting_description;
        String exif_digital_zoom_ratio;
        String exif_exposure_bias_value;
        String exif_exposure_index;
        String exif_exposure_mode;
        String exif_exposure_program;
        String exif_flash_energy;
        String exif_focal_length_in_35mm_film;
        String exif_focal_plane_resolution_unit;
        String exif_focal_plane_x_resolution;
        String exif_focal_plane_y_resolution;
        String exif_gain_control;
        String exif_gps_area_information;
        String exif_gps_differential;
        String exif_gps_dop;
        String exif_gps_measure_mode;
        String exif_image_description;
        String exif_light_source;
        String exif_maker_note;
        String exif_max_aperture_value;
        String exif_metering_mode;
        String exif_oecf;
        String exif_photometric_interpretation;
        String exif_saturation;
        String exif_scene_capture_type;
        String exif_scene_type;
        String exif_sensing_method;
        String exif_sharpness;
        String exif_shutter_speed_value;
        String exif_software;
        String exif_user_comment;
        {
            // tags that are new in Android N - note we skip tags unlikely to be relevant for camera photos
            // update, now available in all Android versions thanks to using AndroidX ExifInterface
            exif_aperture_value = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE);
            exif_brightness_value = exif.getAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE);
            exif_cfa_pattern = exif.getAttribute(ExifInterface.TAG_CFA_PATTERN);
            exif_color_space = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE);
            exif_components_configuration = exif.getAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION);
            exif_compressed_bits_per_pixel = exif.getAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL);
            exif_compression = exif.getAttribute(ExifInterface.TAG_COMPRESSION);
            exif_contrast = exif.getAttribute(ExifInterface.TAG_CONTRAST);
            exif_device_setting_description = exif.getAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION);
            exif_digital_zoom_ratio = exif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO);
            // unclear if we should transfer TAG_EXIF_VERSION - don't want to risk conficting with whatever ExifInterface writes itself
            exif_exposure_bias_value = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE);
            exif_exposure_index = exif.getAttribute(ExifInterface.TAG_EXPOSURE_INDEX);
            exif_exposure_mode = exif.getAttribute(ExifInterface.TAG_EXPOSURE_MODE);
            exif_exposure_program = exif.getAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM);
            exif_flash_energy = exif.getAttribute(ExifInterface.TAG_FLASH_ENERGY);
            exif_focal_length_in_35mm_film = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
            exif_focal_plane_resolution_unit = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT);
            exif_focal_plane_x_resolution = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION);
            exif_focal_plane_y_resolution = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION);
            // TAG_F_NUMBER same as TAG_APERTURE
            exif_gain_control = exif.getAttribute(ExifInterface.TAG_GAIN_CONTROL);
            exif_gps_area_information = exif.getAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION);
            // don't care about TAG_GPS_DEST_*
            exif_gps_differential = exif.getAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL);
            exif_gps_dop = exif.getAttribute(ExifInterface.TAG_GPS_DOP);
            // TAG_GPS_IMG_DIRECTION, TAG_GPS_IMG_DIRECTION_REF won't have been recorded in the image yet - we add this ourselves in setGPSDirectionExif()
            // don't care about TAG_GPS_MAP_DATUM?
            exif_gps_measure_mode = exif.getAttribute(ExifInterface.TAG_GPS_MEASURE_MODE);
            // don't care about TAG_GPS_SATELLITES?
            // don't care about TAG_GPS_STATUS, TAG_GPS_TRACK, TAG_GPS_TRACK_REF, TAG_GPS_VERSION_ID
            exif_image_description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION);
            // unclear what TAG_IMAGE_UNIQUE_ID, TAG_INTEROPERABILITY_INDEX are
            // TAG_ISO_SPEED_RATINGS same as TAG_ISO
            // skip TAG_JPEG_INTERCHANGE_FORMAT, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
            exif_light_source = exif.getAttribute(ExifInterface.TAG_LIGHT_SOURCE);
            exif_maker_note = exif.getAttribute(ExifInterface.TAG_MAKER_NOTE);
            exif_max_aperture_value = exif.getAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE);
            exif_metering_mode = exif.getAttribute(ExifInterface.TAG_METERING_MODE);
            exif_oecf = exif.getAttribute(ExifInterface.TAG_OECF);
            exif_photometric_interpretation = exif.getAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION);
            // skip PIXEL_X/Y_DIMENSION, as it may have changed
            // don't care about TAG_PLANAR_CONFIGURATION
            // don't care about TAG_PRIMARY_CHROMATICITIES, TAG_REFERENCE_BLACK_WHITE?
            // don't care about TAG_RESOLUTION_UNIT
            // TAG_ROWS_PER_STRIP may have changed (if it's even relevant)
            // TAG_SAMPLES_PER_PIXEL may no longer be relevant if we've changed the image dimensions?
            exif_saturation = exif.getAttribute(ExifInterface.TAG_SATURATION);
            exif_scene_capture_type = exif.getAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE);
            exif_scene_type = exif.getAttribute(ExifInterface.TAG_SCENE_TYPE);
            exif_sensing_method = exif.getAttribute(ExifInterface.TAG_SENSING_METHOD);
            exif_sharpness = exif.getAttribute(ExifInterface.TAG_SHARPNESS);
            exif_shutter_speed_value = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE);
            exif_software = exif.getAttribute(ExifInterface.TAG_SOFTWARE);
            // don't care about TAG_SPATIAL_FREQUENCY_RESPONSE, TAG_SPECTRAL_SENSITIVITY?
            // don't care about TAG_STRIP_*
            // don't care about TAG_SUBJECT_*
            // TAG_SUBSEC_TIME_DIGITIZED same as TAG_SUBSEC_TIME_DIG
            // TAG_SUBSEC_TIME_ORIGINAL same as TAG_SUBSEC_TIME_ORIG
            // TAG_THUMBNAIL_IMAGE_* may have changed
            // don't care about TAG_TRANSFER_FUNCTION?
            exif_user_comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT);
            // don't care about TAG_WHITE_POINT?
            // TAG_X_RESOLUTION may have changed?
            // don't care about TAG_Y_*?
        }

        String exif_photographic_sensitivity = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
        String exif_sensitivity_type = exif.getAttribute(ExifInterface.TAG_SENSITIVITY_TYPE);
        String exif_standard_output_sensitivity = exif.getAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY);
        String exif_recommended_exposure_index = exif.getAttribute(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX);
        String exif_iso_speed = exif.getAttribute(ExifInterface.TAG_ISO_SPEED);
        String exif_custom_rendered = exif.getAttribute(ExifInterface.TAG_CUSTOM_RENDERED);
        String exif_lens_specification = exif.getAttribute(ExifInterface.TAG_LENS_SPECIFICATION);
        String exif_lens_name = exif.getAttribute(ExifInterface.TAG_LENS_MAKE);
        String exif_lens_model = exif.getAttribute(ExifInterface.TAG_LENS_MODEL);

        if( MyDebug.LOG )
            Log.d(TAG, "now write new EXIF data");
        if( exif_aperture != null )
            exif_new.setAttribute(ExifInterface.TAG_F_NUMBER, exif_aperture);
        if( exif_exposure_time != null )
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exif_exposure_time);
        if( exif_flash != null )
            exif_new.setAttribute(ExifInterface.TAG_FLASH, exif_flash);
        if( exif_focal_length != null )
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exif_focal_length);
        if( exif_iso != null )
            //noinspection deprecation
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, exif_iso);
        if( exif_make != null )
            exif_new.setAttribute(ExifInterface.TAG_MAKE, exif_make);
        if( exif_model != null )
            exif_new.setAttribute(ExifInterface.TAG_MODEL, exif_model);
        if( exif_white_balance != null )
            exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exif_white_balance);

        {
            if( exif_aperture_value != null )
                exif_new.setAttribute(ExifInterface.TAG_APERTURE_VALUE, exif_aperture_value);
            if( exif_brightness_value != null )
                exif_new.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, exif_brightness_value);
            if( exif_cfa_pattern != null )
                exif_new.setAttribute(ExifInterface.TAG_CFA_PATTERN, exif_cfa_pattern);
            if( exif_color_space != null )
                exif_new.setAttribute(ExifInterface.TAG_COLOR_SPACE, exif_color_space);
            if( exif_components_configuration != null )
                exif_new.setAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION, exif_components_configuration);
            if( exif_compressed_bits_per_pixel != null )
                exif_new.setAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, exif_compressed_bits_per_pixel);
            if( exif_compression != null )
                exif_new.setAttribute(ExifInterface.TAG_COMPRESSION, exif_compression);
            if( exif_contrast != null )
                exif_new.setAttribute(ExifInterface.TAG_CONTRAST, exif_contrast);
            if( exif_device_setting_description != null )
                exif_new.setAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, exif_device_setting_description);
            if( exif_digital_zoom_ratio != null )
                exif_new.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, exif_digital_zoom_ratio);
            if( exif_exposure_bias_value != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, exif_exposure_bias_value);
            if( exif_exposure_index != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_INDEX, exif_exposure_index);
            if( exif_exposure_mode != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, exif_exposure_mode);
            if( exif_exposure_program != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, exif_exposure_program);
            if( exif_flash_energy != null )
                exif_new.setAttribute(ExifInterface.TAG_FLASH_ENERGY, exif_flash_energy);
            if( exif_focal_length_in_35mm_film != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, exif_focal_length_in_35mm_film);
            if( exif_focal_plane_resolution_unit != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, exif_focal_plane_resolution_unit);
            if( exif_focal_plane_x_resolution != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, exif_focal_plane_x_resolution);
            if( exif_focal_plane_y_resolution != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, exif_focal_plane_y_resolution);
            if( exif_gain_control != null )
                exif_new.setAttribute(ExifInterface.TAG_GAIN_CONTROL, exif_gain_control);
            if( exif_gps_area_information != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, exif_gps_area_information);
            if( exif_gps_differential != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL, exif_gps_differential);
            if( exif_gps_dop != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_DOP, exif_gps_dop);
            if( exif_gps_measure_mode != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_MEASURE_MODE, exif_gps_measure_mode);
            if( exif_image_description != null )
                exif_new.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, exif_image_description);
            if( exif_light_source != null )
                exif_new.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, exif_light_source);
            if( exif_maker_note != null )
                exif_new.setAttribute(ExifInterface.TAG_MAKER_NOTE, exif_maker_note);
            if( exif_max_aperture_value != null )
                exif_new.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, exif_max_aperture_value);
            if( exif_metering_mode != null )
                exif_new.setAttribute(ExifInterface.TAG_METERING_MODE, exif_metering_mode);
            if( exif_oecf != null )
                exif_new.setAttribute(ExifInterface.TAG_OECF, exif_oecf);
            if( exif_photometric_interpretation != null )
                exif_new.setAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, exif_photometric_interpretation);
            if( exif_saturation != null )
                exif_new.setAttribute(ExifInterface.TAG_SATURATION, exif_saturation);
            if( exif_scene_capture_type != null )
                exif_new.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, exif_scene_capture_type);
            if( exif_scene_type != null )
                exif_new.setAttribute(ExifInterface.TAG_SCENE_TYPE, exif_scene_type);
            if( exif_sensing_method != null )
                exif_new.setAttribute(ExifInterface.TAG_SENSING_METHOD, exif_sensing_method);
            if( exif_sharpness != null )
                exif_new.setAttribute(ExifInterface.TAG_SHARPNESS, exif_sharpness);
            if( exif_shutter_speed_value != null )
                exif_new.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, exif_shutter_speed_value);
            if( exif_software != null )
                exif_new.setAttribute(ExifInterface.TAG_SOFTWARE, exif_software);
            if( exif_user_comment != null )
                exif_new.setAttribute(ExifInterface.TAG_USER_COMMENT, exif_user_comment);
        }

        if( exif_photographic_sensitivity != null )
            exif_new.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, exif_photographic_sensitivity);
        if( exif_sensitivity_type != null )
            exif_new.setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, exif_sensitivity_type);
        if( exif_standard_output_sensitivity != null )
            exif_new.setAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, exif_standard_output_sensitivity);
        if( exif_recommended_exposure_index != null )
            exif_new.setAttribute(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX, exif_recommended_exposure_index);
        if( exif_iso_speed != null )
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED, exif_iso_speed);
        if( exif_custom_rendered != null )
            exif_new.setAttribute(ExifInterface.TAG_CUSTOM_RENDERED, exif_custom_rendered);
        if( exif_lens_specification != null )
            exif_new.setAttribute(ExifInterface.TAG_LENS_SPECIFICATION, exif_lens_specification);
        if( exif_lens_name != null )
            exif_new.setAttribute(ExifInterface.TAG_LENS_MAKE, exif_lens_name);
        if( exif_lens_model != null )
            exif_new.setAttribute(ExifInterface.TAG_LENS_MODEL, exif_lens_model);

    }

    /** Transfers device exif info related to date and time.
     */
    private static void transferDeviceExifDateTime(ExifInterface exif, ExifInterface exif_new) {
        if( MyDebug.LOG )
            Log.d(TAG, "transferDeviceExifDateTime");

        // tags related to date and time

        String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        String exif_datetime_original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
        String exif_datetime_digitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
        String exif_subsec_time = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME);
        String exif_subsec_time_orig = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL); // previously TAG_SUBSEC_TIME_ORIG
        String exif_subsec_time_dig = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED); // previously TAG_SUBSEC_TIME_DIG
        String exif_offset_time = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME);
        String exif_offset_time_orig = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL);
        String exif_offset_time_dig = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED);

        if( exif_datetime != null )
            exif_new.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
        if( exif_datetime_original != null )
            exif_new.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exif_datetime_original);
        if( exif_datetime_digitized != null )
            exif_new.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exif_datetime_digitized);
        if( exif_subsec_time != null )
            exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME, exif_subsec_time);
        if( exif_subsec_time_orig != null )
            exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, exif_subsec_time_orig);
        if( exif_subsec_time_dig != null )
            exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, exif_subsec_time_dig);
        if( exif_offset_time != null )
            exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME, exif_offset_time);
        if( exif_offset_time_orig != null )
            exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, exif_offset_time_orig);
        if( exif_offset_time_dig != null )
            exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, exif_offset_time_dig);

    }

    /** Transfers device exif info related to gps location.
     */
    private static void transferDeviceExifGPS(ExifInterface exif, ExifInterface exif_new) {
        if( MyDebug.LOG )
            Log.d(TAG, "transferDeviceExifGPS");

        // tags for gps info

        String exif_gps_processing_method = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD);
        String exif_gps_latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
        String exif_gps_latitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
        String exif_gps_longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
        String exif_gps_longitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
        String exif_gps_altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
        String exif_gps_altitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
        String exif_gps_datestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
        String exif_gps_timestamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
        String exif_gps_speed = exif.getAttribute(ExifInterface.TAG_GPS_SPEED);
        String exif_gps_speed_ref = exif.getAttribute(ExifInterface.TAG_GPS_SPEED_REF);

        if( exif_gps_processing_method != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, exif_gps_processing_method);
        if( exif_gps_latitude != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, exif_gps_latitude);
        if( exif_gps_latitude_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exif_gps_latitude_ref);
        if( exif_gps_longitude != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, exif_gps_longitude);
        if( exif_gps_longitude_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exif_gps_longitude_ref);
        if( exif_gps_altitude != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, exif_gps_altitude);
        if( exif_gps_altitude_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, exif_gps_altitude_ref);
        if( exif_gps_datestamp != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, exif_gps_datestamp);
        if( exif_gps_timestamp != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, exif_gps_timestamp);
        if( exif_gps_speed != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED, exif_gps_speed);
        if( exif_gps_speed_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, exif_gps_speed_ref);
    }

    /** Explicitly removes tags based on the RemoveDeviceExif option.
     *  Note that in theory this method is unnecessary: we implement the RemoveDeviceExif options
     *  (if not OFF) by resaving the JPEG via a bitmap, and then limiting what Exif tags are
     *  transferred across. This method is for extra paranoia: first to reduce the risk of future
     *  bugs, secondly just in case saving via a bitmap does ever add exif tags.
     */
    private static void removeExifTags(ExifInterface exif_new, final ImageSaver.Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "removeExifTags");

        if( request.remove_device_exif != ImageSaver.Request.RemoveDeviceExif.OFF ) {
            if( MyDebug.LOG )
                Log.d(TAG, "remove exif tags");
            exif_new.setAttribute(ExifInterface.TAG_F_NUMBER, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, null);
            exif_new.setAttribute(ExifInterface.TAG_FLASH, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, null);
            //noinspection deprecation
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, null);
            exif_new.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, null);
            exif_new.setAttribute(ExifInterface.TAG_MAKE, null);
            exif_new.setAttribute(ExifInterface.TAG_MODEL, null);
            exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, null);
            exif_new.setAttribute(ExifInterface.TAG_APERTURE_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_CFA_PATTERN, null);
            exif_new.setAttribute(ExifInterface.TAG_COLOR_SPACE, null);
            exif_new.setAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION, null);
            exif_new.setAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, null);
            exif_new.setAttribute(ExifInterface.TAG_COMPRESSION, null);
            exif_new.setAttribute(ExifInterface.TAG_CONTRAST, null);
            exif_new.setAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, null);
            exif_new.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_INDEX, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, null);
            exif_new.setAttribute(ExifInterface.TAG_FLASH_ENERGY, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, null);
            exif_new.setAttribute(ExifInterface.TAG_GAIN_CONTROL, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_BEARING, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_BEARING_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_DISTANCE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_DISTANCE_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DOP, null);
            if( !request.store_geo_direction ) {
                exif_new.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, null);
            }
            exif_new.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_MEASURE_MODE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_SATELLITES, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_STATUS, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_TRACK, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_TRACK_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_VERSION_ID, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, null);
            exif_new.setAttribute(ExifInterface.TAG_INTEROPERABILITY_INDEX, null);
            exif_new.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, null);
            exif_new.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, null);
            exif_new.setAttribute(ExifInterface.TAG_MAKER_NOTE, null);
            exif_new.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_METERING_MODE, null);
            exif_new.setAttribute(ExifInterface.TAG_OECF, null);
            exif_new.setAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, null);
            exif_new.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, null);
            exif_new.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, null);
            exif_new.setAttribute(ExifInterface.TAG_PLANAR_CONFIGURATION, null);
            exif_new.setAttribute(ExifInterface.TAG_PRIMARY_CHROMATICITIES, null);
            exif_new.setAttribute(ExifInterface.TAG_REFERENCE_BLACK_WHITE, null);
            exif_new.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, null);
            exif_new.setAttribute(ExifInterface.TAG_ROWS_PER_STRIP, null);
            exif_new.setAttribute(ExifInterface.TAG_SAMPLES_PER_PIXEL, null);
            exif_new.setAttribute(ExifInterface.TAG_SATURATION, null);
            exif_new.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_SCENE_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_SENSING_METHOD, null);
            exif_new.setAttribute(ExifInterface.TAG_SHARPNESS, null);
            exif_new.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_SOFTWARE, null);
            exif_new.setAttribute(ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE, null);
            exif_new.setAttribute(ExifInterface.TAG_SPECTRAL_SENSITIVITY, null);
            exif_new.setAttribute(ExifInterface.TAG_STRIP_BYTE_COUNTS, null);
            exif_new.setAttribute(ExifInterface.TAG_STRIP_OFFSETS, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_AREA, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_LOCATION, null);
            exif_new.setAttribute(ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH, null);
            exif_new.setAttribute(ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_TRANSFER_FUNCTION, null);
            if( !request.store_ypr ) {
                exif_new.setAttribute(ExifInterface.TAG_USER_COMMENT, null);
            }
            exif_new.setAttribute(ExifInterface.TAG_WHITE_POINT, null);
            exif_new.setAttribute(ExifInterface.TAG_X_RESOLUTION, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_CB_CR_COEFFICIENTS, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_CB_CR_POSITIONING, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_RESOLUTION, null);
            if( !(request.custom_tag_artist != null && !request.custom_tag_artist.isEmpty() ) ) {
                exif_new.setAttribute(ExifInterface.TAG_ARTIST, null);
            }
            if( !(request.custom_tag_copyright != null && !request.custom_tag_copyright.isEmpty() ) ) {
                exif_new.setAttribute(ExifInterface.TAG_COPYRIGHT, null);
            }

            exif_new.setAttribute(ExifInterface.TAG_BITS_PER_SAMPLE, null);
            exif_new.setAttribute(ExifInterface.TAG_EXIF_VERSION, null);
            exif_new.setAttribute(ExifInterface.TAG_FLASHPIX_VERSION, null);
            exif_new.setAttribute(ExifInterface.TAG_GAMMA, null);
            exif_new.setAttribute(ExifInterface.TAG_RELATED_SOUND_FILE, null);
            exif_new.setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, null);
            exif_new.setAttribute(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX, null);
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED, null);
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY, null);
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ, null);
            exif_new.setAttribute(ExifInterface.TAG_FILE_SOURCE, null);
            exif_new.setAttribute(ExifInterface.TAG_CUSTOM_RENDERED, null);
            exif_new.setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, null);
            exif_new.setAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_SPECIFICATION, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_MAKE, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_MODEL, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_SERIAL_NUMBER, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, null);
            exif_new.setAttribute(ExifInterface.TAG_DNG_VERSION, null);
            exif_new.setAttribute(ExifInterface.TAG_DEFAULT_CROP_SIZE, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_THUMBNAIL_IMAGE, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_PREVIEW_IMAGE_START, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_ASPECT_FRAME, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_TOP_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_ISO, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_JPG_FROM_RAW, null);
            exif_new.setAttribute(ExifInterface.TAG_XMP, null);
            exif_new.setAttribute(ExifInterface.TAG_NEW_SUBFILE_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBFILE_TYPE, null);

            if( request.remove_device_exif != ImageSaver.Request.RemoveDeviceExif.KEEP_DATETIME ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "remove datetime tags");
                exif_new.setAttribute(ExifInterface.TAG_DATETIME, null);
                exif_new.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null);
                exif_new.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null);
                exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME, null);
                exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, null);
                exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, null);
                exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME, null);
                exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, null);
                exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, null);
            }

            if( !request.store_location ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "remove gps tags");
                exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, null);
            }
        }
    }

    private static void setGPSDirectionExif(ExifInterface exif, boolean store_geo_direction, double geo_direction) {
        if( MyDebug.LOG )
            Log.d(TAG, "setGPSDirectionExif");
        if( store_geo_direction ) {
            float geo_angle = (float)Math.toDegrees(geo_direction);
            if( geo_angle < 0.0f ) {
                geo_angle += 360.0f;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "save geo_angle: " + geo_angle);
            // see http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/GPS.html
            String GPSImgDirection_string = Math.round(geo_angle*100) + "/100";
            if( MyDebug.LOG )
                Log.d(TAG, "GPSImgDirection_string: " + GPSImgDirection_string);
            // fine to ignore request.remove_device_exif, as this is a separate user option
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, GPSImgDirection_string);
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M");
        }
    }

    /** Whether custom exif tags need to be applied to the image file.
     */
    private static boolean hasCustomExif(String custom_tag_artist, String custom_tag_copyright) {
        if( custom_tag_artist != null && !custom_tag_artist.isEmpty() )
            return true;
        if( custom_tag_copyright != null && !custom_tag_copyright.isEmpty() )
            return true;
        return false;
    }

    /** Applies the custom exif tags to the ExifInterface.
     */
    private static void setCustomExif(ExifInterface exif, String custom_tag_artist, String custom_tag_copyright) {
        if( MyDebug.LOG )
            Log.d(TAG, "setCustomExif");
        if( custom_tag_artist != null && !custom_tag_artist.isEmpty() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "apply TAG_ARTIST: " + custom_tag_artist);
            // fine to ignore request.remove_device_exif, as this is a separate user option
            exif.setAttribute(ExifInterface.TAG_ARTIST, custom_tag_artist);
        }
        if( custom_tag_copyright != null && !custom_tag_copyright.isEmpty())  {
            if( MyDebug.LOG )
                Log.d(TAG, "apply TAG_COPYRIGHT: " + custom_tag_copyright);
            // fine to ignore request.remove_device_exif, as this is a separate user option
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, custom_tag_copyright);
        }
    }

    /** Adds exif tags for datetime from the supplied date, if not present. Needed for camera vendor
     *  extensions which (at least on Galaxy S10e) don't seem to have these tags set at all!
     */
    private static void addDateTimeExif(ExifInterface exif, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "addDateTimeExif");
        String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        if( MyDebug.LOG )
            Log.d(TAG, "existing exif TAG_DATETIME: " + exif_datetime);
        if( exif_datetime == null ) {
            SimpleDateFormat date_fmt = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            date_fmt.setTimeZone(TimeZone.getDefault()); // need local timezone for TAG_DATETIME
            exif_datetime = date_fmt.format(current_date);
            if( MyDebug.LOG )
                Log.d(TAG, "new TAG_DATETIME: " + exif_datetime);

            exif.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
            // set these tags too (even if already present, overwrite to be consistent)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exif_datetime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exif_datetime);

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                // XXX requires Android 7
                // needs to be -/+HH:mm format, which is given by XXX
                date_fmt = new SimpleDateFormat("XXX", Locale.US);
                date_fmt.setTimeZone(TimeZone.getDefault());
                String timezone = date_fmt.format(current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "timezone: " + timezone);
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, timezone);
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, timezone);
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, timezone);
            }
        }
    }

    private static void fixGPSTimestamp(ExifInterface exif, Date current_date) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "fixGPSTimestamp");
            Log.d(TAG, "current datestamp: " + exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP));
            Log.d(TAG, "current timestamp: " + exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));
            Log.d(TAG, "current datetime: " + exif.getAttribute(ExifInterface.TAG_DATETIME));
        }
        // Hack: Problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP and TAG_GPS_TIMESTAMP (GPSDateStamp) set (date tends to be around 2038 - possibly a driver bug of casting long to int?).
        // This causes problems when viewing with Gallery apps (e.g., Gallery ICS; Google Photos seems fine however), as they show this incorrect date.
        // Update: Before v1.34 this was "fixed" by calling: exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, Long.toString(System.currentTimeMillis()));
        // However this stopped working on or before 20161006. This wasn't a change in Open Camera (whilst this was working fine in
        // 1.33 when I released it, the bug had come back when I retested that version) and I'm not sure how this ever worked, since
        // TAG_GPS_TIMESTAMP is meant to be a string such "21:45:23", and not the number of ms since 1970 - possibly it wasn't really
        // working , and was simply invalidating it such that Gallery then fell back to looking elsewhere for the datetime?
        // So now hopefully fixed properly...
        // Note, this problem also occurs on OnePlus 3T and Gallery ICS, if we don't have this function called
        SimpleDateFormat date_fmt = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
        date_fmt.setTimeZone(TimeZone.getTimeZone("UTC")); // needs to be UTC time for the GPS datetime tags
        String datestamp = date_fmt.format(current_date);

        SimpleDateFormat time_fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        time_fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = time_fmt.format(current_date);

        if( MyDebug.LOG ) {
            Log.d(TAG, "datestamp: " + datestamp);
            Log.d(TAG, "timestamp: " + timestamp);
        }
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, datestamp);
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, timestamp);

        if( MyDebug.LOG )
            Log.d(TAG, "fixGPSTimestamp exit");
    }

    /** Whether we need to fix up issues with location.
     *  See comments in fixGPSTimestamp(), where some devices with Camera2 need fixes for TAG_GPS_DATESTAMP and TAG_GPS_TIMESTAMP.
     *  Also some devices (e.g. Pixel 6 Pro) have problem that location is not stored in images with Camera2 API, so we need to
     *  enter modifyExif() to add it if not present; similarly Fairphone 5 needs correcting due to storing longitude as 0.0.
     */
    private static boolean needGPSExifFix(boolean is_jpeg, boolean using_camera2, boolean store_location) {
        if( is_jpeg && using_camera2 ) {
            return store_location;
        }
        return false;
    }

    /** Makes various modifications to the exif data, if necessary.
     *  Any fix-ups should respect the setting of RemoveDeviceExif!
     */
    private static void modifyExif(ExifInterface exif, ImageSaver.Request.RemoveDeviceExif remove_device_exif, boolean is_jpeg, boolean using_camera2, boolean using_camera_extensions, Date current_date, boolean store_location, Location location, boolean store_geo_direction, double geo_direction, String custom_tag_artist, String custom_tag_copyright, double level_angle, double pitch_angle, boolean store_ypr) {
        if( MyDebug.LOG )
            Log.d(TAG, "modifyExif");
        setGPSDirectionExif(exif, store_geo_direction, geo_direction);
        if( store_ypr ) {
            float geo_angle = (float)Math.toDegrees(geo_direction);
            if( geo_angle < 0.0f ) {
                geo_angle += 360.0f;
            }
            String encoding = "ASCII\0\0\0";
            // fine to ignore request.remove_device_exif, as this is a separate user option
            //exif.setAttribute(ExifInterface.TAG_USER_COMMENT,"Yaw:" + geo_angle + ",Pitch:" + pitch_angle + ",Roll:" + level_angle);
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT,encoding + "Yaw:" + geo_angle + ",Pitch:" + pitch_angle + ",Roll:" + level_angle);
            if( MyDebug.LOG )
                Log.d(TAG, "UserComment: " + exif.getAttribute(ExifInterface.TAG_USER_COMMENT));
        }
        setCustomExif(exif, custom_tag_artist, custom_tag_copyright);

        boolean force_location = false; // whether we need to add location data
        if( store_location ) {
            // Normally if geotagging is enabled, location should have already been added via the Camera API.
            // But we need this when using camera extensions (since Camera API doesn't support location for camera extensions).
            // And some devices (e.g., Pixel 6 Pro with Camera2 API) seem to not store location data, so we always check if we need to add it.
            // Similarly Fairphone 5 always has longitude stored as 0.0.
            // fine to ignore request.remove_device_exif, as this is a separate user option
            if( !exif.hasAttribute(ExifInterface.TAG_GPS_LATITUDE) || !exif.hasAttribute(ExifInterface.TAG_GPS_LONGITUDE) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "force location as not present in exif");
                force_location = true;
            }
            else {
                double [] lat_long = exif.getLatLong();
                if( lat_long == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "force location as not present in exif");
                    force_location = true;
                }
                else if( lat_long[0] == 0.0 || lat_long[1] == 0.0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "force location as longitude or latitude is 0.0");
                    force_location = true;
                }
            }
        }
        if( force_location ) {
            if( MyDebug.LOG )
                Log.d(TAG, "force store location"); // don't log location for privacy reasons!
            exif.setGpsInfo(location);
        }

        if( using_camera_extensions ) {
            if( remove_device_exif == ImageSaver.Request.RemoveDeviceExif.OFF || remove_device_exif == ImageSaver.Request.RemoveDeviceExif.KEEP_DATETIME ) {
                addDateTimeExif(exif, current_date);
            }
        }
        else if( needGPSExifFix(is_jpeg, using_camera2, store_location) ) {
            // fine to ignore request.remove_device_exif, as this is a separate user option
            fixGPSTimestamp(exif, current_date);
        }
    }

    /* In some cases we may create an ExifInterface with a FileDescriptor obtained from a
     * ParcelFileDescriptor (via getFileDescriptor()). It's important to keep a reference to the
     * ParcelFileDescriptor object for as long as the exif interface, otherwise there's a risk of
     * the ParcelFileDescriptor being garbage collected, invalidating the file descriptor still
     * being used by the ExifInterface!
     * This didn't cause any known bugs, but good practice to fix, similar to the issue reported in
     * https://sourceforge.net/p/opencamera/tickets/417/ .
     * Also important to call the close() method when done with it, to close the
     * ParcelFileDescriptor (if one was created).
     */
    private static class ExifInterfaceHolder {
        // see documentation above about keeping hold of pdf due to the garbage collector!
        private final ParcelFileDescriptor pfd;
        private final ExifInterface exif;

        ExifInterfaceHolder(ParcelFileDescriptor pfd, ExifInterface exif) {
            this.pfd = pfd;
            this.exif = exif;
        }

        ExifInterface getExif() {
            return this.exif;
        }

        void close()  {
            if( this.pfd != null ) {
                try {
                    this.pfd.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to close parcelfiledescriptor", e);
                }
            }
        }
    }

    /** Creates a new exif interface for reading and writing.
     *  If picFile==null, then saveUri must be non-null, and will be used instead to write the exif
     *  tags too.
     *  The returned ExifInterfaceHolder will always be non-null, but the contained getExif() may
     *  return null if this method was unable to create the exif interface.
     *  The caller should call close() on the returned ExifInterfaceHolder when no longer required.
     */
    private static ExifInterfaceHolder createExifInterface(Context context, File picFile, Uri saveUri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = null;
        ExifInterface exif = null;
        if( picFile != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "write to picFile: " + picFile);
            exif = new ExifInterface(picFile.getAbsolutePath());
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "write direct to saveUri: " + saveUri);
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(saveUri, "rw");
            if( parcelFileDescriptor != null ) {
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                exif = new ExifInterface(fileDescriptor);
            }
            else {
                Log.e(TAG, "failed to create ParcelFileDescriptor for saveUri: " + saveUri);
            }
        }
        return new ExifInterfaceHolder(parcelFileDescriptor, exif);
    }

    /** Makes various modifications to the saved image file, according to the preferences in request.
     *  This method is used when saving directly from the JPEG data rather than a bitmap.
     *  If picFile==null, then saveUri must be non-null, and will be used instead to write the exif
     *  tags too.
     */
    static void updateExif(Context context, ImageSaver.Request request, File picFile, Uri saveUri) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "updateExif: " + picFile);
        if( request.store_geo_direction || request.store_ypr || hasCustomExif(request.custom_tag_artist, request.custom_tag_copyright) ||
                request.using_camera_extensions || // when using camera extensions, we need to call modifyExif() to fix up various missing tags
                needGPSExifFix(request.type == ImageSaver.Request.Type.JPEG, request.using_camera2, request.store_location) ) {
            long time_s = System.currentTimeMillis();
            if( MyDebug.LOG )
                Log.d(TAG, "add additional exif info");
            try {
                ExifInterfaceHolder exif_holder = createExifInterface(context, picFile, saveUri);
                if( MyDebug.LOG )
                    Log.d(TAG, "*** time after create exif: " + (System.currentTimeMillis() - time_s));
                try {
                    ExifInterface exif = exif_holder.getExif();
                    if( exif != null ) {
                        modifyExif(exif, request.remove_device_exif, request.type == ImageSaver.Request.Type.JPEG, request.using_camera2, request.using_camera_extensions, request.current_date, request.store_location, request.location, request.store_geo_direction, request.geo_direction, request.custom_tag_artist, request.custom_tag_copyright, request.level_angle, request.pitch_angle, request.store_ypr);

                        if( MyDebug.LOG )
                            Log.d(TAG, "*** time after modifyExif: " + (System.currentTimeMillis() - time_s));
                        exif.saveAttributes();
                        if( MyDebug.LOG )
                            Log.d(TAG, "*** time after saveAttributes: " + (System.currentTimeMillis() - time_s));
                    }
                }
                finally {
                    exif_holder.close();
                }
            }
            catch(NoClassDefFoundError e) {
                // have had Google Play crashes from new ExifInterface() elsewhere for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn), so also catch here just in case
                MyDebug.logStackTrace(TAG, "exif orientation NoClassDefFoundError", e);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "*** time to add additional exif info: " + (System.currentTimeMillis() - time_s));
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "no exif data to update for: " + picFile);
        }
    }

    /** Transfers exif tags from exif to exif_new, and then applies any extra Exif tags according to the preferences in the request.
     *  Note that we use several ExifInterface tags that are now deprecated in API level 23 and 24. These are replaced with new tags that have
     *  the same string value (e.g., TAG_APERTURE replaced with TAG_F_NUMBER, but both have value "FNumber"). We use the deprecated versions
     *  to avoid complicating the code (we'd still have to read the deprecated values for older devices).
     */
    private static void setExif(final ImageSaver.Request request, ExifInterface exif, ExifInterface exif_new) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "setExif");

        if( request.remove_device_exif == ImageSaver.Request.RemoveDeviceExif.OFF ) {
            transferDeviceExif(exif, exif_new);
        }

        if( request.remove_device_exif == ImageSaver.Request.RemoveDeviceExif.OFF || request.remove_device_exif == ImageSaver.Request.RemoveDeviceExif.KEEP_DATETIME ) {
            transferDeviceExifDateTime(exif, exif_new);
        }

        if( request.remove_device_exif == ImageSaver.Request.RemoveDeviceExif.OFF || request.store_location ) {
            // If geotagging is enabled, we explicitly override the remove_device_exif setting.
            // Arguably we don't need an if statement here at all - but if there was some device strangely
            // setting GPS tags even when we haven't set them, it's better to remove them if the user has not
            // requested RemoveDeviceExif.OFF.
            transferDeviceExifGPS(exif, exif_new);
        }

        modifyExif(exif_new, request.remove_device_exif, request.type == ImageSaver.Request.Type.JPEG, request.using_camera2, request.using_camera_extensions, request.current_date, request.store_location, request.location, request.store_geo_direction, request.geo_direction, request.custom_tag_artist, request.custom_tag_copyright, request.level_angle, request.pitch_angle, request.store_ypr);

        removeExifTags(exif_new, request); // must be last, before saving attributes
        exif_new.saveAttributes();
    }

    /** Transfers exif tags from the JPEG data to the image file, and then applies any extra Exif tags according to the preferences in the request.
     */
    static void setExifFromData(final ImageSaver.Request request, byte[] data, File to_file) throws IOException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setExifFromData");
            Log.d(TAG, "to_file: " + to_file);
        }
        InputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(data);
            ExifInterface exif = new ExifInterface(inputStream);
            ExifInterface exif_new = new ExifInterface(to_file.getAbsolutePath());
            setExif(request, exif, exif_new);
        }
        finally {
            if( inputStream != null ) {
                inputStream.close();
            }
        }
    }

    /** Transfers exif tags from the JPEG data to the image file descriptor, and then applies any extra Exif tags according to the preferences in the request.
     */
    static void setExifFromData(final ImageSaver.Request request, byte[] data, FileDescriptor to_file_descriptor) throws IOException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setExifFromData");
            Log.d(TAG, "to_file_descriptor: " + to_file_descriptor);
        }
        InputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(data);
            ExifInterface exif = new ExifInterface(inputStream);
            ExifInterface exif_new = new ExifInterface(to_file_descriptor);
            setExif(request, exif, exif_new);
        }
        finally {
            if( inputStream != null ) {
                inputStream.close();
            }
        }
    }
}

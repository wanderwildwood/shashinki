package net.sourceforge.opencamera.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.ToastBoxer;
import net.sourceforge.opencamera.preview.ApplicationInterface;

import java.util.Arrays;
import java.util.List;

/** This contains functionality related to the (mainly customisable) on-screen icons.
 *  To add a new customisable on-screen icon:
 *  - Add the button to addOnScreenIcons().
 *  - If the icon image or content description should depend on something persistent (e.g., saved
 *    preference), then add to updateOnScreenIcons(), with a corresponding new update*Icon() method.
 *  - Add to setVisibility() (with a corresponding new show*Icon() method).
 *  - Add to checkDisableGUIIcons().
 *  - Add a new clicked*() method, and call this from a corresponding onClick method in MainActivity.
 *  - Adding the corresponding preference for whether to show the on-screen icon or not (in
 *    preferences_sub_gui.xml).
 */
public class OnScreenIcons {
    private static final String TAG = "OnScreenIcons";

    private final MainActivity main_activity;

    private final ToastBoxer exposure_lock_toast = new ToastBoxer();
    private final ToastBoxer white_balance_lock_toast = new ToastBoxer();
    private final ToastBoxer store_location_toast = new ToastBoxer();
    private final ToastBoxer stamp_toast = new ToastBoxer();
    private final ToastBoxer face_detection_toast = new ToastBoxer();
    private final ToastBoxer cycle_lock_orientation_toast = new ToastBoxer();
    private final ToastBoxer preview_shots_toast = new ToastBoxer();

    public OnScreenIcons(MainActivity main_activity) {
        if( MyDebug.LOG )
            Log.d(TAG, "OnScreenIcons");
        this.main_activity = main_activity;
    }

    /** Adds the on-screen icons (whether enabled or not) to the supplied list.
     */
    void addOnScreenIcons(List<View> buttons) {
        buttons.add(main_activity.findViewById(R.id.exposure_lock));
        buttons.add(main_activity.findViewById(R.id.white_balance_lock));
        buttons.add(main_activity.findViewById(R.id.cycle_raw));
        buttons.add(main_activity.findViewById(R.id.store_location));
        buttons.add(main_activity.findViewById(R.id.text_stamp));
        buttons.add(main_activity.findViewById(R.id.stamp));
        buttons.add(main_activity.findViewById(R.id.focus_peaking));
        buttons.add(main_activity.findViewById(R.id.auto_level));
        buttons.add(main_activity.findViewById(R.id.cycle_flash));
        buttons.add(main_activity.findViewById(R.id.face_detection));
        buttons.add(main_activity.findViewById(R.id.audio_control));
        buttons.add(main_activity.findViewById(R.id.cycle_lock_orientation));
        buttons.add(main_activity.findViewById(R.id.preview_shots));
    }

    public void updateOnScreenIcons() {
        if( MyDebug.LOG )
            Log.d(TAG, "updateOnScreenIcons");
        this.updateExposureLockIcon();
        this.updateWhiteBalanceLockIcon();
        this.updateCycleRawIcon();
        this.updateStoreLocationIcon();
        this.updateTextStampIcon();
        this.updateStampIcon();
        this.updateFocusPeakingIcon();
        this.updateAutoLevelIcon();
        this.updateCycleFlashIcon();
        this.updateFaceDetectionIcon();
        this.updateCycleLockOrientationIcon();
        this.updatePreviewShotsIcon();
    }

    private void updateExposureLockIcon() {
        ImageButton view = main_activity.findViewById(R.id.exposure_lock);
        boolean enabled = main_activity.getPreview().isExposureLocked();
        view.setImageResource(enabled ? R.drawable.exposure_locked : R.drawable.exposure_unlocked);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.exposure_unlock : R.string.exposure_lock) );
    }

    private void updateWhiteBalanceLockIcon() {
        ImageButton view = main_activity.findViewById(R.id.white_balance_lock);
        boolean enabled = main_activity.getPreview().isWhiteBalanceLocked();
        view.setImageResource(enabled ? R.drawable.white_balance_locked : R.drawable.white_balance_unlocked);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.white_balance_unlock : R.string.white_balance_lock) );
    }

    private void updateCycleRawIcon() {
        ApplicationInterface.RawPref raw_pref = main_activity.getApplicationInterface().getRawPref();
        ImageButton view = main_activity.findViewById(R.id.cycle_raw);
        if( raw_pref == ApplicationInterface.RawPref.RAWPREF_JPEG_DNG ) {
            if( main_activity.getApplicationInterface().isRawOnly() ) {
                // actually RAW only
                view.setImageResource(R.drawable.raw_only_icon);
            }
            else {
                view.setImageResource(R.drawable.raw_icon);
            }
        }
        else {
            view.setImageResource(R.drawable.raw_off_icon);
        }
    }

    private void updateStoreLocationIcon() {
        ImageButton view = main_activity.findViewById(R.id.store_location);
        boolean enabled = main_activity.getApplicationInterface().getGeotaggingPref();
        view.setImageResource(enabled ? R.drawable.ic_gps_fixed_red_48dp : R.drawable.ic_gps_fixed_white_48dp);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.preference_location_disable : R.string.preference_location_enable) );
    }

    private void updateTextStampIcon() {
        ImageButton view = main_activity.findViewById(R.id.text_stamp);
        boolean enabled = !main_activity.getApplicationInterface().getTextStampPref().isEmpty();
        view.setImageResource(enabled ? R.drawable.baseline_text_fields_red_48 : R.drawable.baseline_text_fields_white_48);
    }

    private void updateStampIcon() {
        ImageButton view = main_activity.findViewById(R.id.stamp);
        boolean enabled = main_activity.getApplicationInterface().getStampPref().equals("preference_stamp_yes");
        view.setImageResource(enabled ? R.drawable.ic_text_format_red_48dp : R.drawable.ic_text_format_white_48dp);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.stamp_disable : R.string.stamp_enable) );
    }

    private void updateFocusPeakingIcon() {
        ImageButton view = main_activity.findViewById(R.id.focus_peaking);
        boolean enabled = main_activity.getApplicationInterface().getFocusPeakingPref();
        view.setImageResource(enabled ? R.drawable.key_visualizer_red : R.drawable.key_visualizer);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.focus_peaking_disable : R.string.focus_peaking_enable) );
    }

    private void updateAutoLevelIcon() {
        ImageButton view = main_activity.findViewById(R.id.auto_level);
        boolean enabled = main_activity.getApplicationInterface().getAutoStabilisePref();
        view.setImageResource(enabled ? R.drawable.auto_stabilise_icon_red : R.drawable.auto_stabilise_icon);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.auto_level_disable : R.string.auto_level_enable) );
    }

    private void updateCycleFlashIcon() {
        // n.b., read from preview rather than saved application preference - so the icon updates correctly when in flash
        // auto mode, but user switches to manual ISO where flash auto isn't supported
        String flash_value = main_activity.getPreview().getCurrentFlashValue();
        if( flash_value != null ) {
            ImageButton view = main_activity.findViewById(R.id.cycle_flash);
            switch( flash_value ) {
                case "flash_off":
                    view.setImageResource(R.drawable.flash_off);
                    break;
                case "flash_auto":
                case "flash_frontscreen_auto":
                    view.setImageResource(R.drawable.flash_auto);
                    break;
                case "flash_on":
                case "flash_frontscreen_on":
                    view.setImageResource(R.drawable.flash_on);
                    break;
                case "flash_torch":
                case "flash_frontscreen_torch":
                    view.setImageResource(R.drawable.baseline_highlight_white_48);
                    break;
                case "flash_red_eye":
                    view.setImageResource(R.drawable.baseline_remove_red_eye_white_48);
                    break;
                default:
                    // just in case??
                    Log.e(TAG, "unknown flash value " + flash_value);
                    view.setImageResource(R.drawable.flash_off);
                    break;
            }
        }
        else {
            ImageButton view = main_activity.findViewById(R.id.cycle_flash);
            view.setImageResource(R.drawable.flash_off);
        }
    }

    private void updateFaceDetectionIcon() {
        ImageButton view = main_activity.findViewById(R.id.face_detection);
        boolean enabled = main_activity.getApplicationInterface().getFaceDetectionPref();
        view.setImageResource(enabled ? R.drawable.ic_face_red_48dp : R.drawable.ic_face_white_48dp);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.face_detection_disable : R.string.face_detection_enable) );
    }

    private void updateCycleLockOrientationIcon() {
        ImageButton view = main_activity.findViewById(R.id.cycle_lock_orientation);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String pref = sharedPreferences.getString(PreferenceKeys.LockOrientationPreferenceKey, "none");

        switch( pref ) {
            case "portrait":
                view.setImageResource(R.drawable.mobile_lock_portrait_48px_red);
                break;
            case "landscape":
                view.setImageResource(R.drawable.mobile_lock_landscape_48px_red);
                break;
            case "none":
                view.setImageResource(R.drawable.mobile_unlock_48px);
                break;
            default:
                // just in case??
                Log.e(TAG, "unknown lock orientation " + pref);
                view.setImageResource(R.drawable.mobile_unlock_48px);
                break;
        }
    }

    private void updatePreviewShotsIcon() {
        ImageButton view = main_activity.findViewById(R.id.preview_shots);
        boolean enabled = main_activity.getApplicationInterface().getPreShotsPref(main_activity.getApplicationInterface().getPhotoMode());
        view.setImageResource(enabled ? R.drawable.motion_photos_on_48px_red : R.drawable.motion_photos_on_48px);
        view.setContentDescription( main_activity.getResources().getString(enabled ? R.string.preview_shots_disable : R.string.preview_shots_enable) );
    }

    /** Sets the visibility flag for on-screen icons.
     * @param visibility Visibility flag.
     * @param visibility_video Visibility flag to use for icons that are still allowed when recording video
     */
    void setVisibility(int visibility, int visibility_video) {
        View exposureLockButton = main_activity.findViewById(R.id.exposure_lock);
        View whiteBalanceLockButton = main_activity.findViewById(R.id.white_balance_lock);
        View cycleRawButton = main_activity.findViewById(R.id.cycle_raw);
        View storeLocationButton = main_activity.findViewById(R.id.store_location);
        View textStampButton = main_activity.findViewById(R.id.text_stamp);
        View stampButton = main_activity.findViewById(R.id.stamp);
        View focusPeakingButton = main_activity.findViewById(R.id.focus_peaking);
        View autoLevelButton = main_activity.findViewById(R.id.auto_level);
        View cycleFlashButton = main_activity.findViewById(R.id.cycle_flash);
        View faceDetectionButton = main_activity.findViewById(R.id.face_detection);
        View audioControlButton = main_activity.findViewById(R.id.audio_control);
        View cycleLockOrientationButton = main_activity.findViewById(R.id.cycle_lock_orientation);
        View previewShotsButton = main_activity.findViewById(R.id.preview_shots);

        if( showExposureLockIcon() )
            exposureLockButton.setVisibility(visibility_video); // still allow exposure lock when recording video
        if( showWhiteBalanceLockIcon() )
            whiteBalanceLockButton.setVisibility(visibility_video); // still allow white balance lock when recording video
        if( showCycleRawIcon() )
            cycleRawButton.setVisibility(visibility);
        if( showStoreLocationIcon() )
            storeLocationButton.setVisibility(visibility);
        if( showTextStampIcon() )
            textStampButton.setVisibility(visibility);
        if( showStampIcon() )
            stampButton.setVisibility(visibility);
        if( showFocusPeakingIcon() )
            focusPeakingButton.setVisibility(visibility);
        if( showAutoLevelIcon() )
            autoLevelButton.setVisibility(visibility);
        if( showCycleFlashIcon() )
            cycleFlashButton.setVisibility(visibility);
        if( showFaceDetectionIcon() )
            faceDetectionButton.setVisibility(visibility);
        if( showAudioControlIcon() )
            audioControlButton.setVisibility(visibility);
        if( showCycleLockOrientationIcon() )
            cycleLockOrientationButton.setVisibility(visibility);
        if( showPreviewShotsIcon() )
            previewShotsButton.setVisibility(visibility);
    }

    /** Disables the optional on-screen icons if either user doesn't want to enable them, or not
     *  supported). Note that displaying icons is done via MainUI.showGUI.
     * @return Whether an icon's visibility was changed.
     */
    public boolean checkDisableGUIIcons() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkDisableGUIIcons");
        boolean changed = false;
        if( !main_activity.supportsExposureButton() ) {
            View button = main_activity.findViewById(R.id.exposure);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showExposureLockIcon() ) {
            View button = main_activity.findViewById(R.id.exposure_lock);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showWhiteBalanceLockIcon() ) {
            View button = main_activity.findViewById(R.id.white_balance_lock);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showCycleRawIcon() ) {
            View button = main_activity.findViewById(R.id.cycle_raw);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showStoreLocationIcon() ) {
            View button = main_activity.findViewById(R.id.store_location);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showTextStampIcon() ) {
            View button = main_activity.findViewById(R.id.text_stamp);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showStampIcon() ) {
            View button = main_activity.findViewById(R.id.stamp);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showFocusPeakingIcon() ) {
            View button = main_activity.findViewById(R.id.focus_peaking);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showAutoLevelIcon() ) {
            View button = main_activity.findViewById(R.id.auto_level);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showCycleFlashIcon() ) {
            View button = main_activity.findViewById(R.id.cycle_flash);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showFaceDetectionIcon() ) {
            View button = main_activity.findViewById(R.id.face_detection);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showAudioControlIcon() ) {
            View button = main_activity.findViewById(R.id.audio_control);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showCycleLockOrientationIcon() ) {
            View button = main_activity.findViewById(R.id.cycle_lock_orientation);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showPreviewShotsIcon() ) {
            View button = main_activity.findViewById(R.id.preview_shots);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !main_activity.showSwitchMultiCamIcon() ) {
            // also handle the multi-cam icon here, as this can change when switching between front/back cameras
            // (e.g., if say a device only has multiple back cameras)
            View button = main_activity.findViewById(R.id.switch_multi_camera);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "checkDisableGUIIcons: " + changed);
        return changed;
    }

    private boolean showExposureLockIcon() {
        if( !main_activity.getPreview().supportsExposureLock() )
            return false;
        if( main_activity.getApplicationInterface().isCameraExtensionPref() ) {
            // not supported for camera extensions
            return false;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowExposureLockPreferenceKey, true);
    }

    private boolean showWhiteBalanceLockIcon() {
        if( !main_activity.getPreview().supportsWhiteBalanceLock() )
            return false;
        if( main_activity.getApplicationInterface().isCameraExtensionPref() ) {
            // not supported for camera extensions
            return false;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowWhiteBalanceLockPreferenceKey, false);
    }

    private boolean showCycleRawIcon() {
        if( !main_activity.getPreview().supportsRaw() )
            return false;
        if( !main_activity.getApplicationInterface().isRawAllowed(main_activity.getApplicationInterface().getPhotoMode()) )
            return false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowCycleRawPreferenceKey, false);
    }

    private boolean showStoreLocationIcon() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowStoreLocationPreferenceKey, false);
    }

    private boolean showTextStampIcon() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowTextStampPreferenceKey, false);
    }

    private boolean showStampIcon() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowStampPreferenceKey, false);
    }

    private boolean showFocusPeakingIcon() {
        if( !main_activity.supportsPreviewBitmaps() )
            return false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowFocusPeakingPreferenceKey, false);
    }

    boolean showAutoLevelIcon() {
        if( !main_activity.supportsAutoStabilise() )
            return false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowAutoLevelPreferenceKey, false);
    }

    boolean showCycleFlashIcon() {
        if( !main_activity.getPreview().supportsFlash() )
            return false;
        if( main_activity.getPreview().isVideo() )
            return false; // no point showing flash icon in video mode, as we only allow flash auto and flash torch, and we don't support torch on the on-screen cycle flash icon
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowCycleFlashPreferenceKey, false);
    }

    private boolean showFaceDetectionIcon() {
        if( !main_activity.getPreview().supportsFaceDetection() )
            return false;
        if( main_activity.getApplicationInterface().isCameraExtensionPref() ) {
            // not supported for camera extensions
            return false;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowFaceDetectionPreferenceKey, false);
    }

    public boolean showAudioControlIcon() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String audio_control = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none");
        /*if( audio_control.equals("voice") ) {
            return speechControl.hasSpeechRecognition();
        }
        else*/ if( audio_control.equals("noise") ) {
            return true;
        }
        return false;
    }

    private boolean showCycleLockOrientationIcon() {
        if( main_activity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama )
            return false; // see MyApplicationInterface.getLockOrientationPref(): for now panorama only supports portrait
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowCycleLockOrientationPreferenceKey, false);
    }

    private boolean showPreviewShotsIcon() {
        if( !main_activity.supportsPreShots() )
            return false;
        MyApplicationInterface.PhotoMode photo_mode = main_activity.getApplicationInterface().getPhotoMode();
        if( main_activity.getPreview().isVideo() || photo_mode == MyApplicationInterface.PhotoMode.ExpoBracketing || photo_mode == MyApplicationInterface.PhotoMode.FocusBracketing || photo_mode == MyApplicationInterface.PhotoMode.Panorama ) {
            // see MyApplicationInterface.getLockOrientationPref()
            return false;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        return sharedPreferences.getBoolean(PreferenceKeys.ShowPreviewShotsPreferenceKey, false);
    }

    public void clickedExposureLock() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedExposureLock");
        main_activity.getPreview().toggleExposureLock();
        updateExposureLockIcon();
        main_activity.getPreview().showToast(exposure_lock_toast, main_activity.getPreview().isExposureLocked() ? R.string.exposure_locked : R.string.exposure_unlocked, true);
    }

    public void clickedWhiteBalanceLock() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedWhiteBalanceLock");
        main_activity.getPreview().toggleWhiteBalanceLock();
        updateWhiteBalanceLockIcon();
        main_activity.getPreview().showToast(white_balance_lock_toast, main_activity.getPreview().isWhiteBalanceLocked() ? R.string.white_balance_locked : R.string.white_balance_unlocked, true);
    }

    public void clickedCycleRaw() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedCycleRaw");

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String new_value = null;
        switch( sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no") ) {
            case "preference_raw_no":
                new_value = "preference_raw_yes";
                break;
            case "preference_raw_yes":
                new_value = "preference_raw_only";
                break;
            case "preference_raw_only":
                new_value = "preference_raw_no";
                break;
            default:
                Log.e(TAG, "unrecognised raw preference");
                break;
        }
        if( new_value != null ) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(PreferenceKeys.RawPreferenceKey, new_value);
            editor.apply();

            updateCycleRawIcon();
            main_activity.getApplicationInterface().getDrawPreview().updateSettings();
            main_activity.getPreview().reopenCamera(); // needed for RAW options to take effect
        }
    }

    public void clickedStoreLocation() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedStoreLocation");
        boolean value = main_activity.getApplicationInterface().getGeotaggingPref();
        value = !value;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.LocationPreferenceKey, value);
        editor.apply();

        updateStoreLocationIcon();
        main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // because we cache the geotagging setting
        main_activity.initLocation(); // required to enable or disable GPS, also requests permission if necessary
        main_activity.closePopup();

        String message = main_activity.getResources().getString(R.string.preference_location) + ": " + main_activity.getResources().getString(value ? R.string.on : R.string.off);
        main_activity.getPreview().showToast(store_location_toast, message, true);
    }

    public void clickedTextStamp() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedTextStamp");
        main_activity.closePopup();

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(main_activity);
        alertDialog.setTitle(R.string.preference_textstamp);

        final View dialog_view = LayoutInflater.from(main_activity).inflate(R.layout.alertdialog_edittext, null);
        final EditText editText = dialog_view.findViewById(R.id.edit_text);
        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
        editText.setHint(main_activity.getResources().getString(R.string.preference_textstamp));
        editText.setText(main_activity.getApplicationInterface().getTextStampPref());
        alertDialog.setView(dialog_view);
        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if( MyDebug.LOG )
                    Log.d(TAG, "custom text stamp clicked okay");

                String custom_text = editText.getText().toString();
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.TextStampPreferenceKey, custom_text);
                editor.apply();

                updateTextStampIcon();
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog alert = alertDialog.create();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                if( MyDebug.LOG )
                    Log.d(TAG, "custom stamp text dialog dismissed");
                main_activity.setWindowFlagsForCamera();
                main_activity.showPreview(true);
            }
        });

        main_activity.showPreview(false);
        main_activity.setWindowFlagsForSettings(true);
        main_activity.showAlert(alert);
    }

    public void clickedStamp() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedStamp");

        main_activity.closePopup();

        boolean value = main_activity.getApplicationInterface().getStampPref().equals("preference_stamp_yes");
        value = !value;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.StampPreferenceKey, value ? "preference_stamp_yes" : "preference_stamp_no");
        editor.apply();

        updateStampIcon();
        main_activity.getApplicationInterface().getDrawPreview().updateSettings();
        main_activity.getPreview().showToast(stamp_toast, value ? R.string.stamp_enabled : R.string.stamp_disabled, true);
    }

    public void clickedFocusPeaking() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedFocusPeaking");
        boolean value = main_activity.getApplicationInterface().getFocusPeakingPref();
        value = !value;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.FocusPeakingPreferenceKey, value ? "preference_focus_peaking_on" : "preference_focus_peaking_off");
        editor.apply();

        updateFocusPeakingIcon();
        main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // needed to update focus peaking
    }

    public void clickedAutoLevel() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedAutoLevel");
        boolean value = main_activity.getApplicationInterface().getAutoStabilisePref();
        value = !value;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, value);
        editor.apply();

        boolean done_dialog = false;
        if( value ) {
            boolean done_auto_stabilise_info = sharedPreferences.contains(PreferenceKeys.AutoStabiliseInfoPreferenceKey);
            if( !done_auto_stabilise_info ) {
                main_activity.getMainUI().showInfoDialog(R.string.preference_auto_stabilise, R.string.auto_stabilise_info, PreferenceKeys.AutoStabiliseInfoPreferenceKey);
                done_dialog = true;
            }
        }

        if( !done_dialog ) {
            String message = main_activity.getResources().getString(R.string.preference_auto_stabilise) + ": " + main_activity.getResources().getString(value ? R.string.on : R.string.off);
            main_activity.getPreview().showToast(main_activity.getChangedAutoStabiliseToastBoxer(), message, true);
        }

        updateAutoLevelIcon();
        main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // because we cache the auto-stabilise setting
        main_activity.closePopup();
    }

    public void clickedCycleFlash() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedCycleFlash");

        main_activity.getPreview().cycleFlash(true, true);
        updateCycleFlashIcon();
    }

    public void clickedFaceDetection() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedFaceDetection");

        main_activity.closePopup();

        boolean value = main_activity.getApplicationInterface().getFaceDetectionPref();
        value = !value;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.FaceDetectionPreferenceKey, value);
        editor.apply();

        updateFaceDetectionIcon();
        main_activity.getPreview().showToast(face_detection_toast, value ? R.string.face_detection_enabled : R.string.face_detection_disabled, true);
        main_activity.reopenCamera(true);
    }

    public void clickedAudioControl() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedAudioControl");
        // check hasAudioControl just in case!
        if( !showAudioControlIcon() ) {
            if( MyDebug.LOG )
                Log.e(TAG, "clickedAudioControl, but hasAudioControl returns false!");
            return;
        }
        main_activity.closePopup();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String audio_control = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none");
        /*if( audio_control.equals("voice") && speechControl.hasSpeechRecognition() ) {
            if( speechControl.isStarted() ) {
                speechControl.stopListening();
            }
            else {
                boolean has_audio_permission = true;
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
                    if( MyDebug.LOG )
                        Log.d(TAG, "check for record audio permission");
                    if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "record audio permission not available");
                        applicationInterface.requestRecordAudioPermission();
                        has_audio_permission = false;
                    }
                }
                if( has_audio_permission ) {
                    speechControl.showToast(true);
                    speechControl.startSpeechRecognizerIntent();
                    speechControl.speechRecognizerStarted();
                }
            }
        }
        else*/ if( audio_control.equals("noise") ){
            if( main_activity.hasAudioListener() ) {
                main_activity.freeAudioListener(false);
            }
            else {
                main_activity.startAudioListener();
            }
        }
    }

    public void clickedCycleLockOrientation() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedCycleLockOrientation");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String new_value = null;

        switch( sharedPreferences.getString(PreferenceKeys.LockOrientationPreferenceKey, "none") ) {
            case "none":
                new_value = "portrait";
                break;
            case "portrait":
                new_value = "landscape";
                break;
            case "landscape":
                new_value = "none";
                break;
            default:
                Log.e(TAG, "unrecognised lock orientation preference");
                break;
        }

        if( new_value != null ) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(PreferenceKeys.LockOrientationPreferenceKey, new_value);
            editor.apply();

            updateCycleLockOrientationIcon();
            {
                String [] entries_array = main_activity.getResources().getStringArray(R.array.preference_lock_orientation_entries);
                String [] values_array = main_activity.getResources().getStringArray(R.array.preference_lock_orientation_values);
                int index = Arrays.asList(values_array).indexOf(new_value);
                if( index != -1 ) { // just in case!
                    main_activity.getPreview().showToast(cycle_lock_orientation_toast, entries_array[index], true);
                }
            }
        }
    }

    public void clickedPreviewShots() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedPreviewShots");

        boolean value = main_activity.getApplicationInterface().getPreShotsPref(main_activity.getApplicationInterface().getPhotoMode());
        value = !value;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.PreShotsPreferenceKey, value ? "preference_save_preshots_on" : "preference_save_preshots_off");
        editor.apply();

        updatePreviewShotsIcon();
        main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // needed to update preview shots
        main_activity.getPreview().showToast(preview_shots_toast, value ? R.string.preview_shots_enabled : R.string.preview_shots_disabled, true);
    }
}

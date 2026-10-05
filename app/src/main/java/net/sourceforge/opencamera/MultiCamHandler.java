package net.sourceforge.opencamera;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.CameraControllerManager;

import java.util.ArrayList;
import java.util.List;

/** Handling of multiple cameras.
 */
public class MultiCamHandler {
    private static final String TAG = "MultiCamHandler";

    // whether this is a multi-camera device (note, this isn't simply having more than 1 camera, but also having more than one with the same facing)
    // note that in most cases, code should check the MultiCamButtonPreferenceKey preference as well as the is_multi_cam flag,
    // this can be done via isMultiCamEnabled().
    private boolean is_multi_cam;
    // These lists are lists of camera IDs with the same "facing" (front, back or external).
    // Only initialised if is_multi_cam==true.
    private List<Integer> back_camera_ids;
    private List<Integer> front_camera_ids;
    private List<Integer> other_camera_ids;

    MultiCamHandler(CameraControllerManager camera_controller_manager) {
        if( MyDebug.LOG )
            Log.d(TAG, "MultiCamHandler");

        // We only allow the separate icon for switching cameras if:
        // - there are at least 2 types of "facing" camera, and
        // - there are at least 2 cameras with the same "facing".
        // If there are multiple cameras but all with different "facing", then the switch camera
        // icon is used to iterate over all cameras.
        // If there are more than two cameras, but all cameras have the same "facing, we still stick
        // with using the switch camera icon to iterate over all cameras.
        final int n_cameras = camera_controller_manager.getNumberOfCameras();
        if( n_cameras > 2 ) {
            this.back_camera_ids = new ArrayList<>();
            this.front_camera_ids = new ArrayList<>();
            this.other_camera_ids = new ArrayList<>();
            for(int i=0;i<n_cameras;i++) {
                switch( camera_controller_manager.getFacing(i) ) {
                    case FACING_BACK:
                        back_camera_ids.add(i);
                        break;
                    case FACING_FRONT:
                        front_camera_ids.add(i);
                        break;
                    default:
                        // we assume any unknown cameras are also external
                        other_camera_ids.add(i);
                        break;
                }
            }
            boolean multi_same_facing = back_camera_ids.size() >= 2 || front_camera_ids.size() >= 2 || other_camera_ids.size() >= 2;
            int n_facing = 0;
            if( !back_camera_ids.isEmpty() )
                n_facing++;
            if( !front_camera_ids.isEmpty() )
                n_facing++;
            if( !other_camera_ids.isEmpty() )
                n_facing++;
            this.is_multi_cam = multi_same_facing && n_facing >= 2;
            //this.is_multi_cam = false; // test
            if( MyDebug.LOG ) {
                Log.d(TAG, "multi_same_facing: " + multi_same_facing);
                Log.d(TAG, "n_facing: " + n_facing);
                Log.d(TAG, "is_multi_cam: " + is_multi_cam);
            }

            if( !is_multi_cam ) {
                this.back_camera_ids = null;
                this.front_camera_ids = null;
                this.other_camera_ids = null;
            }
        }
    }

    /** Whether this is a multi camera device, and the user preference is set to enable the multi-camera button.
     */
    boolean isMultiCamEnabled(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return is_multi_cam && sharedPreferences.getBoolean(PreferenceKeys.MultiCamButtonPreferenceKey, true);
    }

    /** Whether this is a multi camera device, whether or not the user preference is set to enable
     *  the multi-camera button.
     */
    boolean isMultiCam() {
        return is_multi_cam;
    }

    /** Whether the device is a multi cam device, and has more than 1 camera for a particular facing.
     */
    boolean hasMultiCameras(CameraController.Facing facing) {
        if( is_multi_cam ) {
            switch( facing ) {
                case FACING_BACK:
                    if( back_camera_ids.size() > 1 )
                        return true;
                    break;
                case FACING_FRONT:
                    if( front_camera_ids.size() > 1 )
                        return true;
                    break;
                default:
                    if( other_camera_ids.size() > 1 )
                        return true;
                    break;
            }
        }
        return false;
    }

    /* Returns the cameraId that the "Switch camera" button will switch to.
     * Note that this may not necessarily be the next camera ID, on multi camera devices (if
     * isMultiCamEnabled() returns true).
     */
    int getNextCameraId(Context context, CameraControllerManager camera_controller_manager, int cameraId) {
        if( isMultiCamEnabled(context) ) {
            // don't use preview.getCameraController(), as it may be null if user quickly switches between cameras
            switch( camera_controller_manager.getFacing(cameraId) ) {
                case FACING_BACK:
                    if( !front_camera_ids.isEmpty() )
                        cameraId = front_camera_ids.get(0);
                    else if( !other_camera_ids.isEmpty() )
                        cameraId = other_camera_ids.get(0);
                    break;
                case FACING_FRONT:
                    if( !other_camera_ids.isEmpty() )
                        cameraId = other_camera_ids.get(0);
                    else if( !back_camera_ids.isEmpty() )
                        cameraId = back_camera_ids.get(0);
                    break;
                default:
                    if( !back_camera_ids.isEmpty() )
                        cameraId = back_camera_ids.get(0);
                    else if( !front_camera_ids.isEmpty() )
                        cameraId = front_camera_ids.get(0);
                    break;
            }
        }
        else {
            int n_cameras = camera_controller_manager.getNumberOfCameras();
            cameraId = (cameraId+1) % n_cameras;
        }
        return cameraId;
    }

    /** Returns list of logical cameras with same facing as this_facing.
     */
    public List<Integer> getSameFacingLogicalCameras(CameraControllerManager camera_controller_manager, CameraController.Facing this_facing) {
        List<Integer> logical_camera_ids = new ArrayList<>();
        for(int i=0;i<camera_controller_manager.getNumberOfCameras();i++) {
            if( camera_controller_manager.getFacing(i) != this_facing ) {
                // only show cameras with same facing
                continue;
            }
            logical_camera_ids.add(i);
        }
        return logical_camera_ids;
    }
}

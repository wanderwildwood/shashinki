package net.sourceforge.opencamera;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

/**
 * Where another app asks a photo or video taken for it to be written (MediaStore.EXTRA_OUTPUT).
 *
 * The write happens as Lens, so an address is taken only when it is a content:// address another
 * app gave out: never a file:// path (which could name Lens's own settings or other private files)
 * and never one of Lens's own providers. Any other address is ignored, and the picture is saved
 * where Lens saves its own and handed back from there.
 */
public final class CaptureOutput {

    private CaptureOutput() {
    }

    /** The caller's output address, or null when there is none or it is not one Lens will write to. */
    public static Uri from(Context context, Bundle extras) {
        if( extras == null )
            return null;
        Uri uri;
        try {
            uri = extras.getParcelable(MediaStore.EXTRA_OUTPUT);
        }
        catch(RuntimeException e) {
            return null;
        }
        if( uri == null )
            return null;
        return acceptable(uri.getScheme(), uri.getAuthority(), context.getPackageName()) ? uri : null;
    }

    /** True for a content:// address whose provider is not Lens's own. */
    static boolean acceptable(String scheme, String authority, String ownPackage) {
        if( !ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(scheme) )
            return false;
        if( authority == null || authority.isEmpty() )
            return false;
        String a = authority.toLowerCase(java.util.Locale.ROOT);
        String own = ownPackage.toLowerCase(java.util.Locale.ROOT);
        // An authority may carry user info ("user@host"); the provider is what follows the '@'.
        int at = a.lastIndexOf('@');
        if( at >= 0 )
            a = a.substring(at + 1);
        return !a.equals(own) && !a.startsWith(own + ".");
    }

    /** True when Lens was opened to take a photo or video for another app. */
    static boolean isForAnotherApp(String action) {
        return MediaStore.ACTION_IMAGE_CAPTURE.equals(action) ||
                MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) ||
                MediaStore.ACTION_VIDEO_CAPTURE.equals(action);
    }

    /** Whether a photo or video carries where it was taken (and which way the camera faced).
     *  One taken for another app never does, whatever "Where a photo was taken" says: the
     *  picture leaves Lens at once, and the person asked that app for a picture, not a place.
     */
    static boolean storesLocation(String action, boolean setting) {
        return setting && !isForAnotherApp(action);
    }
}

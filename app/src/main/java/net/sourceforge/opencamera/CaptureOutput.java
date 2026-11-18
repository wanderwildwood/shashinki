package net.sourceforge.opencamera;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Where another app asks a photo or video taken for it to be written (MediaStore.EXTRA_OUTPUT).
 *
 * The write happens as Lens, so an address is taken only when it is a content:// address another
 * app gave out: never a file:// path (which could name Lens's own settings or other private files)
 * and never one of Lens's own providers. Nor an address Lens could write even without that app's
 * leave: a photo or video Lens saved itself (Android lets the app that made a media-store item
 * write to it) or anything in the folder Lens was given for its own pictures. Any other address
 * is ignored: a video is then saved where Lens saves its own and handed back from there, and a
 * photo is saved there too while only a small preview goes back in the result.
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
        String own = context.getPackageName();
        if( !acceptable(uri.getScheme(), uri.getAuthority(), own) )
            return null;
        if( underOwnGrant(uri.getAuthority(), grantedAuthorities(context)) )
            return null;
        if( isMediaStore(uri.getAuthority()) && ownedBy(mediaOwner(context, uri), own) )
            return null;
        return uri;
    }

    /** The providers Lens holds a lasting write grant on (the folder chosen for its pictures). */
    private static List<String> grantedAuthorities(Context context) {
        List<String> authorities = new ArrayList<>();
        try {
            for(UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                if( permission.isWritePermission() && permission.getUri() != null )
                    authorities.add(permission.getUri().getAuthority());
            }
        }
        catch(RuntimeException e) {
            // none known
        }
        return authorities;
    }

    /** Who made a media-store item, or null when Lens cannot see it (then Lens cannot write it either). */
    private static String mediaOwner(Context context, Uri uri) {
        try( Cursor cursor = context.getContentResolver().query(uri,
                new String[]{MediaStore.MediaColumns.OWNER_PACKAGE_NAME}, null, null, null) ) {
            if( cursor != null && cursor.moveToFirst() )
                return cursor.getString(0);
        }
        catch(RuntimeException e) {
            // not readable by Lens
        }
        return null;
    }

    /** True for the media store's own provider. */
    static boolean isMediaStore(String authority) {
        return authority != null && MediaStore.AUTHORITY.equalsIgnoreCase(stripUserInfo(authority));
    }

    /** True when a media-store item was made by Lens itself. */
    static boolean ownedBy(String owner, String ownPackage) {
        return owner != null && owner.equalsIgnoreCase(ownPackage);
    }

    /** True when the address is on a provider where Lens already holds a lasting write grant. */
    static boolean underOwnGrant(String authority, List<String> grantedAuthorities) {
        if( authority == null )
            return false;
        String a = stripUserInfo(authority);
        for(String granted : grantedAuthorities) {
            if( granted != null && stripUserInfo(granted).equalsIgnoreCase(a) )
                return true;
        }
        return false;
    }

    /** An authority may carry user info ("user@host"); the provider is what follows the '@'. */
    private static String stripUserInfo(String authority) {
        int at = authority.lastIndexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    /** True for a content:// address whose provider is not Lens's own. */
    static boolean acceptable(String scheme, String authority, String ownPackage) {
        if( !ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(scheme) )
            return false;
        if( authority == null || authority.isEmpty() )
            return false;
        String a = stripUserInfo(authority.toLowerCase(java.util.Locale.ROOT));
        String own = ownPackage.toLowerCase(java.util.Locale.ROOT);
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

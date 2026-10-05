package net.sourceforge.opencamera;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

/** Functionality related to the save location.
 */
public class SaveLocationHandler {
    private static final String TAG = "SaveLocationHandler";

    private final MainActivity main_activity;

    private final SaveLocationHistory save_location_history; // save location for non-SAF
    private SaveLocationHistory save_location_history_saf; // save location for SAF (only initialised when SAF is used)

    public SaveLocationHandler(MainActivity main_activity) {
        this.main_activity = main_activity;

        save_location_history = new SaveLocationHistory(main_activity, PreferenceKeys.SaveLocationHistoryBasePreferenceKey, main_activity.getStorageUtils().getSaveLocation());
        checkSaveLocations();
        if( main_activity.getStorageUtils().isUsingSAF() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "create new SaveLocationHistory for SAF");
            save_location_history_saf = new SaveLocationHistory(main_activity, PreferenceKeys.SaveLocationHistorySAFBasePreferenceKey, main_activity.getStorageUtils().getSaveLocationSAF());
        }
    }

    /** Handles users updating to a version with scoped storage (this could be Android 10 users upgrading
     *  to the version of Open Camera with scoped storage; or users who later upgrade to Android 10).
     *  With scoped storage, we no longer support saving outside of DCIM/ when not using SAF.
     *  This updates if necessary both the current save location, and the save folder history.
     */
    private void checkSaveLocations() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkSaveLocations");
        if( MainActivity.useScopedStorage() ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            boolean any_changes = false;
            String save_location = main_activity.getStorageUtils().getSaveLocation();
            CheckSaveLocationResult res = checkSaveLocation(save_location);
            if( !res.res ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "save_location not valid with scoped storage: " + save_location);
                String new_folder;
                if( res.alt == null ) {
                    // no alternative, fall back to default
                    new_folder = "OpenCamera";
                }
                else {
                    // replace with the alternative
                    if( MyDebug.LOG )
                        Log.d(TAG, "alternative: " + res.alt);
                    new_folder = res.alt;
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.SaveLocationPreferenceKey, new_folder);
                editor.apply();
                any_changes = true;
            }

            // now check history
            // go backwards so we can remove easily
            for(int i=save_location_history.size()-1;i>=0;i--) {
                String this_location = save_location_history.get(i);
                res = checkSaveLocation(this_location);
                if( !res.res ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "save_location in history " + i + " not valid with scoped storage: " + this_location);
                    if( res.alt == null ) {
                        // no alternative, remove
                        save_location_history.remove(i);
                    }
                    else {
                        // replace with the alternative
                        if( MyDebug.LOG )
                            Log.d(TAG, "alternative: " + res.alt);
                        save_location_history.set(i, res.alt);
                    }
                    any_changes = true;
                }
            }

            if( any_changes ) {
                this.save_location_history.updateFolderHistory(main_activity.getStorageUtils().getSaveLocation(), false);
            }
        }
    }

    /** Result from checkSaveLocation. Ideally we'd just use android.util.Pair, but that's not mocked
     *  for use in unit tests.
     *  See checkSaveLocation() for documentation.
     */
    public static class CheckSaveLocationResult {
        final boolean res;
        final String alt;

        public CheckSaveLocationResult(boolean res, String alt) {
            this.res = res;
            this.alt = alt;
        }

        @Override
        public boolean equals(Object o) {
            if( !(o instanceof CheckSaveLocationResult) ) {
                return false;
            }
            CheckSaveLocationResult that = (CheckSaveLocationResult)o;
            // stop dumb inspection that suggests replacing warning with an error(!) (Objects class is not available on all API versions)
            // and the other inspection suggests replacing with code that would cause a nullpointerexception
            //noinspection EqualsReplaceableByObjectsCall,StringEquality
            return that.res == this.res && ( (that.alt == this.alt) || (that.alt != null && that.alt.equals(this.alt) ) );
            //return that.res == this.res && ( (that.alt == this.alt) || (that.alt != null && that.alt.equals(this.alt) ) );
        }

        @Override
        public int hashCode() {
            return (res ? 1249 : 1259) ^ (alt == null ? 0 : alt.hashCode());
        }

        @NonNull
        @Override
        public String toString() {
            return "CheckSaveLocationResult{" + res + " , " + alt + "}";
        }
    }

    public static CheckSaveLocationResult checkSaveLocation(final String folder) {
        return checkSaveLocation(folder, null);
    }

    /** Checks to see if the supplied folder (in the format as used by our preferences) is supported
     *  with scoped storage.
     * @return The Boolean is always non-null, and returns whether the save location is valid.
     *         If the return is false, then if the String is non-null, this stores an alternative
     *         form that is valid. If null, there is no valid alternative.
     * @param base_folder This should normally be null, but can be used to specify manually the
     *                    folder instead of using StorageUtils.getBaseFolder() - needed for unit
     *                    tests as Environment class (for Environment.getExternalStoragePublicDirectory())
     *                    is not mocked.
     */
    public static CheckSaveLocationResult checkSaveLocation(final String folder, String base_folder) {
        /*if( MyDebug.LOG )
            Log.d(TAG, "DCIM path: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());*/
        if( StorageUtils.saveFolderIsFull(folder) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "checkSaveLocation for full path: " + folder);
            // But still check to see if the full path is part of DCIM. Since when using the
            // file dialog method with non-scoped storage, if the user specifies multiple subfolders
            // e.g. DCIM/blah_a/blah_b, we don't spot that in FolderChooserDialog.useFolder(), and
            // instead still store that as the full path.

            if( base_folder == null )
                base_folder = StorageUtils.getBaseFolder().getAbsolutePath();
            // strip '/' as last character - makes it easier to also spot cases where the folder is the
            // DCIM folder, but doesn't have a '/' last character
            if( !base_folder.isEmpty() && base_folder.charAt(base_folder.length()-1) == '/' )
                base_folder = base_folder.substring(0, base_folder.length()-1);
            if( MyDebug.LOG )
                Log.d(TAG, "    compare to base_folder: " + base_folder);
            String alt_folder = null;
            if( folder.startsWith(base_folder) ) {
                alt_folder = folder.substring(base_folder.length());
                // also need to strip the first '/' if it exists
                if( !alt_folder.isEmpty() && alt_folder.charAt(0) == '/' )
                    alt_folder = alt_folder.substring(1);
            }

            return new CheckSaveLocationResult(false, alt_folder);
        }
        else {
            // already in expected format (indicates a sub-folder of DCIM)
            return new CheckSaveLocationResult(true, null);
        }
    }

    /** Call when the SAF save history has been updated.
     *  This is only public so we can call from testing.
     * @param save_folder The new SAF save folder Uri.
     */
    public void updateFolderHistorySAF(String save_folder) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateSaveHistorySAF");
        if( save_location_history_saf == null ) {
            save_location_history_saf = new SaveLocationHistory(main_activity, "save_location_history_saf", save_folder);
        }
        save_location_history_saf.updateFolderHistory(save_folder, true);
    }

    /** Update the save folder (for non-SAF methods).
     */
    void updateSaveFolder(String new_save_location) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateSaveFolder: " + new_save_location);
        if( new_save_location != null ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            String orig_save_location = main_activity.getStorageUtils().getSaveLocation();

            if( !orig_save_location.equals(new_save_location) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "changed save_folder to: " + main_activity.getStorageUtils().getSaveLocation());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.SaveLocationPreferenceKey, new_save_location);
                editor.apply();

                this.save_location_history.updateFolderHistory(main_activity.getStorageUtils().getSaveLocation(), true);
                String save_folder_name = getHumanReadableSaveFolder(main_activity.getStorageUtils().getSaveLocation());
                main_activity.getPreview().showToast(null, main_activity.getResources().getString(R.string.changed_save_location) + "\n" + save_folder_name);
            }
        }
    }

    /** Processes a user specified save folder. This should be used with the non-SAF scoped storage
     *  method, where the user types a folder directly.
     */
    public static String processUserSaveLocation(String folder) {
        // filter repeated '/', e.g., replace // with /:
        String strip = "//";
        while( !folder.isEmpty() && folder.contains(strip) ) {
            folder = folder.replaceAll(strip, "/");
        }

        if( !folder.isEmpty() && folder.charAt(0) == '/' ) {
            // strip '/' as first character - as absolute paths not allowed with scoped storage
            // whilst we do block entering a '/' as first character in the InputFilter, users could
            // get around this (e.g., put a '/' as second character, then delete the first character)
            folder = folder.substring(1);
        }

        if( !folder.isEmpty() && folder.charAt(folder.length()-1) == '/' ) {
            // strip '/' as last character - MediaStore will ignore it, but seems cleaner to strip it out anyway
            // (we still need to allow '/' as last character in the InputFilter, otherwise users won't be able to type it whilst writing a subfolder)
            folder = folder.substring(0, folder.length()-1);
        }

        return folder;
    }

    /** Creates a dialog builder for specifying a save folder dialog (used when not using SAF,
     *  and on scoped storage, as an alternative to using FolderChooserDialog).
     */
    public AlertDialog.Builder createSaveFolderDialog() {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(main_activity);
        alertDialog.setTitle(R.string.preference_save_location);

        final View dialog_view = LayoutInflater.from(main_activity).inflate(R.layout.alertdialog_edittext, null);
        final EditText editText = dialog_view.findViewById(R.id.edit_text);

        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
        editText.setHint(main_activity.getResources().getString(R.string.preference_save_location));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        editText.setText(sharedPreferences.getString(PreferenceKeys.SaveLocationPreferenceKey, "OpenCamera"));
        InputFilter filter = new InputFilter() {
            // whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
            final String disallowed = "|\\?*<\":>";
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for(int i=start;i<end;i++) {
                    if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
                        return "";
                    }
                }
                // also check for '/', not allowed at start
                if( dstart == 0 && start < source.length() && source.charAt(start) == '/' ) {
                    return "";
                }
                return null;
            }
        };
        editText.setFilters(new InputFilter[]{filter});

        alertDialog.setView(dialog_view);

        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if( MyDebug.LOG )
                    Log.d(TAG, "save location clicked okay");

                String folder = editText.getText().toString();
                folder = processUserSaveLocation(folder);

                updateSaveFolder(folder);
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel, null);

        return alertDialog;
    }

    /** Returns a human readable string for the save_folder (as stored in the preferences).
     */
    String getHumanReadableSaveFolder(String save_folder) {
        if( main_activity.getStorageUtils().isUsingSAF() ) {
            // try to get human readable form if possible
            String file_name = main_activity.getStorageUtils().getFilePathFromDocumentUriSAF(Uri.parse(save_folder), true);
            if( file_name != null ) {
                save_folder = file_name;
            }
        }
        else {
            // The strings can either be a sub-folder of DCIM, or (pre-scoped-storage) a full path, so normally either can be displayed.
            // But with scoped storage, an empty string is used to mean DCIM, so seems clearer to say that instead of displaying a blank line!
            if( MainActivity.useScopedStorage() && save_folder.isEmpty() ) {
                save_folder = "DCIM";
            }
        }
        return save_folder;
    }

    /** Clears the non-SAF folder history.
     */
    public void clearFolderHistory() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearFolderHistory");
        save_location_history.clearFolderHistory(main_activity.getStorageUtils().getSaveLocation());
    }

    /** Clears the SAF folder history.
     */
    public void clearFolderHistorySAF() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearFolderHistorySAF");
        save_location_history_saf.clearFolderHistory(main_activity.getStorageUtils().getSaveLocationSAF());
    }

    public void usedFolderPicker() {
        if( main_activity.getStorageUtils().isUsingSAF() ) {
            save_location_history_saf.updateFolderHistory(main_activity.getStorageUtils().getSaveLocationSAF(), true);
        }
        else {
            save_location_history.updateFolderHistory(main_activity.getStorageUtils().getSaveLocation(), true);
        }
    }

    public SaveLocationHistory getSaveLocationHistory() {
        return this.save_location_history;
    }

    public SaveLocationHistory getSaveLocationHistorySAF() {
        return this.save_location_history_saf;
    }

}

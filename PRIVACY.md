# Privacy

Camera takes photos and videos and keeps them on the phone. It has no way to send anything
anywhere.

That is the whole policy. The rest of this page is the evidence for it.

## Four permissions

```
android.permission.CAMERA
android.permission.RECORD_AUDIO
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_COARSE_LOCATION
```

- **Camera**, to take pictures.
- **Microphone**, for a video's sound. It is used only while a video is recording.
- **Location**, only if "Where a photo was taken" is turned on in settings, which it is not to
  begin with. Then each photo carries where it was taken, written into the file itself — and
  anyone the photo is sent to can read it, which the setting says.

There is **no `INTERNET` permission**. Android will not let the app open a network
connection, so nothing it records can leave the phone except by you sending a file.

Open Camera's own build also asks for Bluetooth, for its remote-control feature. That
feature is not offered here, and the permissions are taken out of the manifest.

## Where things go

Photos and videos are saved through Android's media store to `DCIM/OpenCamera` (or the folder
chosen in settings), where any gallery on the phone can see them. Nothing else is written
except the settings, in the app's own preferences.

## No analytics

No crash reporting, no telemetry, no advertising identifier, no third-party SDK. Open
Camera has none either.

## Checking any of this for yourself

```
aapt2 dump badging app-release.apk | grep uses-permission
```

prints the four above and one more,
`com.wanderwildwood.shashinki.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. That one is not
mine: AndroidX defines it for every app, scoped to this package so only this app can hold
it, so that a broadcast receiver registered at runtime is not exported. It grants access to
nothing. There is no `INTERNET` in the list.

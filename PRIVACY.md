# Privacy

Lens takes photos and videos and keeps them on the phone. It has no way to send anything
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

Photos and videos are saved through Android's media store to `DCIM/Camera` (or the folder
chosen in settings), where any gallery on the phone can see them. When you press Save to Wallet
on a code the camera has read, the code is drawn as a picture and kept in the app's own cache
(one file, replaced by the next); it is handed only to Wallet. Nothing else is written except the
settings, in the app's own preferences.

## Outside services

Lens itself talks to no one. Two buttons hand something to another app, and only when pressed:
**Look up**, on a shop or book barcode, gives the number to the phone's web search, or opens
DuckDuckGo with it in the browser when the phone has no search app; and a web address read from a
code opens in the browser. What the search engine or the website receives is covered by its own
privacy policy ([DuckDuckGo's](https://duckduckgo.com/privacy)), not by this one.

## No analytics

No crash reporting, no telemetry, no advertising identifier, no third-party SDK. Open
Camera itself has none either.

## Checking any of this for yourself

```
aapt2 dump badging app-release.apk | grep uses-permission
```

prints the four above and one more,
`com.wanderwildwood.shashinki.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. That one is not
mine: AndroidX defines it for every app, scoped to this package so only this app can hold
it, so that a broadcast receiver registered at runtime is not exported. It grants access to
nothing. There is no `INTERNET` in the list.

<!-- shared-about privacy section -->
## Who we are

Lens is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Lens keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Lens is sold or shared.

Since Lens keeps what it stores only on your phone, you can exercise most of these rights yourself: clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->

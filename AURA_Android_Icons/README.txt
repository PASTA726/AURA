AURA Android launcher icon assets

Copy the contents of app/src/main/res/ into your Android app module's src/main/res/ directory.
Then set in AndroidManifest.xml:
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"

Includes legacy density PNGs, Android 8.0+ adaptive icon XML, and a 512x512 Play Store icon.

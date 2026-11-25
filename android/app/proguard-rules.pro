# Logs never contain addresses, page titles or cookies in release builds (SPEC, "Data protection").
# GeckoView logs every page's address at debug level, with no setting to turn it off, so release
# builds drop debug and verbose log calls everywhere, GeckoView's included.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# GeckoView's passkey fallback refers to Google's play-services-fido, which the build leaves out
# (ADR 0002, ADR 0006). Passkeys go through Android's Credential Manager instead.
-dontwarn com.google.android.gms.**

# Buzz X9 EQ

An Android equalizer built for the Dubstep Buzz X9 TWS earbuds, which ship without a
companion app. Kotlin + Jetpack Compose, min API 28, targets API 35. Built for a
CMF Phone 2 Pro but nothing in it is model-specific.

## What it actually does, and what it cannot

**It cannot change anything inside the earbuds.** The Buzz X9 manual documents only touch
gestures handled by the buds' own firmware. There is no published control protocol, and
with no vendor app in existence there is nothing to reverse-engineer from. Any app
claiming to "tune your budget TWS" is doing what this one does: processing on the phone.

**It equalizes on the phone, before encoding.** A ten-band `DynamicsProcessing` chain is
attached to the global audio output mix, so it covers Spotify, YouTube, games, everything.
Sonically the result in your ears is the same as if the buds had done it.

**One platform limitation matters.** If your phone has Bluetooth A2DP hardware offload
enabled, the Bluetooth audio path can bypass the software effects chain entirely and the
equalizer will silently do nothing over Bluetooth. The Probe tab has a test tone so you
can find out in five seconds, and the fix is a Developer Options toggle (below).

## Screens

| Tab | What it is for |
| --- | --- |
| **Equalizer** | Ten ISO-octave bands at ±12 dB, each labelled with what actually lives in it and an optional plain-language guide. Eight presets plus your own saved curves. Isolation mode. Curve preview and automatic pre-amp headroom. |
| **Device** | Which device is connected and on which profiles, bind one as "my Buzz X9", auto-arm so the curve only goes live for those buds, per-earbud charge where the platform will report it, and the gesture cheat-sheet from the manual. |
| **Probe** | Test tone diagnostic, plus a read-only BLE/SDP dump of what the earbuds advertise. |

### Why the Probe tab exists

Budget TWS are built on chipsets that usually *do* have a control protocol even when the
brand ships no app — JieLi's RCSP over a `0xAE00`-range BLE service or over classic SPP,
Airoha's RACE channel, and so on. The probe dumps the buds' GATT table and SDP records and
flags anything matching a known vendor signature. If something turns up, real on-device EQ
and gesture remapping become buildable. If nothing does, phone-side EQ is the ceiling and
you will know for certain rather than guessing.

The probe only ever reads. It never writes to the earbuds.

### Isolation mode is not noise cancellation

The Buzz X9 advertises ENC. ENC cleans up *your voice* on the microphone so the person you
are calling hears less of your background. It does nothing to what you hear, it runs only
during calls, and it lives in the earbud firmware. These buds have no ANC at all, and no
app can add it — cancelling sound requires a microphone and a speaker inside the ear, both
under the earbud's control, which is a hardware property.

What Isolation mode does instead is real and it is what actually helps on a bus: multiband
compression. In a noisy place the quiet parts of a track fall below the ambient noise floor
and vanish, so you turn the volume up and then get blasted by the chorus. Compressing each
frequency band separately lifts the quiet passages and leaves the loud ones alone. The EQ
curve is untouched, so tonal balance does not change.

It needs a compressor stage from the audio HAL. If the HAL refuses one, the app says so and
disables the mode rather than pretending — and the equalizer keeps working regardless.

### Battery

Android has no public API for earbud charge. The app tries four routes in order —
`getMetadata()` for per-earbud figures, `getBatteryLevel()` for a combined one, the hidden
`BATTERY_LEVEL_CHANGED` broadcast, and the BLE Battery Service if the Probe tab finds one —
and shows whichever answers, with a panel listing exactly what was tried. Separate left and
right readings need a vendor pairing protocol that no-name TWS generally do not implement,
so a single combined figure is the realistic best case. Nothing is ever estimated.

## Building the APK

There is no local toolchain requirement — GitHub Actions builds it.

1. Create an empty GitHub repository.
2. Push this directory to it:

   ```bash
   git remote add origin https://github.com/<you>/BuzzX9EQ.git
   git branch -M main
   git push -u origin main
   ```

3. Open the **Actions** tab. The `Build APK` workflow starts on push (or run it manually
   via *Run workflow*). It takes roughly three to five minutes on a cold cache.
4. Open the finished run and download the **BuzzX9EQ-apk** artifact. Unzip it to get
   `BuzzX9EQ-debug.apk`.

The APK is debug-signed, which is all that sideloading needs.

## Releasing

Tagging publishes a GitHub Release with the APK attached, which is the whole distribution
story for an app like this — no store account, no review, no fee:

```bash
git tag v1.1
git push origin v1.1
```

The `Release` workflow builds, writes a changelog from the commits since the previous tag,
and attaches `BuzzX9EQ-1.1.apk` to the release. You can also run it by hand from the
Actions tab, passing the tag to create. Anyone with the link installs it the same way you
did, and [Obtainium](https://github.com/ImranR98/Obtainium) will track the repo and
auto-update from releases the way a store would.

### Signed releases

Without signing secrets the workflow ships a **debug-signed** APK, and CI generates a fresh
debug key on every run. Android refuses to install an update whose signature differs from
the installed copy, so each release would need the previous one uninstalled first — losing
your saved presets with it.

Fixing that permanently means one real keystore, held in repository secrets and never
committed:

1. Generate it (needs `keytool`, which ships with any JDK):

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias buzzx9 \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Base64-encode it so it survives as a secret:

   ```bash
   base64 -w0 release.jks > release.jks.b64     # Git Bash
   ```

3. In **Settings → Secrets and variables → Actions**, add four repository secrets:
   `KEYSTORE_BASE64` (the contents of the `.b64` file), `KEYSTORE_PASSWORD`, `KEY_ALIAS`
   (`buzzx9`), and `KEY_PASSWORD`.

4. Back up `release.jks` somewhere safe and **never commit it**. Lose it and no future
   build can ever upgrade an installed copy — every user has to uninstall and start over.

The build reads these from the environment, so a missing keystore just means an unsigned
release type. Local builds are unaffected.

## Installing on the phone

Transfer the APK to the phone and open it. Nothing OS will ask you to allow installing
unknown apps from whichever app you opened it with — grant that, then install.

On first launch grant Bluetooth and notification permissions. Without Bluetooth permission
the app still equalizes, but it cannot tell which device is playing, so auto-arm is dead.

## First run

1. Connect the Buzz X9.
2. **Device** tab → find them in the list → **Bind as my Buzz X9**.
3. **Equalizer** tab → switch it on → pick **Reference**.
4. **Probe** tab → play the 62 Hz tone, go back to the Equalizer, and drag the 62 band
   from −12 to +12. The tone should change loudness noticeably.

If it does not change, open Settings → System → Developer options → **Disable Bluetooth
A2DP hardware offloading**, reboot, and try again. (Developer options are unlocked by
tapping Build number seven times in Settings → About phone.)

## About the presets

`Reference` is an educated starting point, not a measured correction. No frequency-response
measurement of the Buzz X9 has been published and this app cannot make one. It assumes the
failure modes cheap TWS drivers share: an upper-bass hump around 125 Hz that muddies
everything above it, scooped lower mids, and a presence peak near 8 kHz. Trust your ears
over the label.

## Layout

```
app/src/main/java/dev/sriyansh/buzzx9/
  MainActivity.kt          three-tab Compose shell, runtime permissions
  audio/Bands.kt           the ten crossover frequencies and their plain-language guide
  audio/Presets.kt         built-in curves plus save/delete for user presets
  audio/Isolation.kt       compressor settings behind Isolation mode
  audio/EqRepo.kt          settings state, persisted to SharedPreferences
  audio/EqEngine.kt        DynamicsProcessing chain, legacy Equalizer fallback
  audio/TestTone.kt        AudioTrack sine generator for the diagnostic
  bt/BtMonitor.kt          A2DP and HFP connection state
  bt/BatteryReader.kt      the four battery routes, and what each one said
  bt/BtEventReceiver.kt    restarts the service on boot and on ACL connect
  bt/GattProbe.kt          BLE scan, GATT dump, SDP dump, vendor fingerprints
  service/EqService.kt     foreground service that owns the effect chain
  ui/                      the three screens plus the theme
```

The effect chain has to be owned by something long-lived, because Android releases an
`AudioEffect` as soon as its creating object is collected. That is what the foreground
service and its persistent notification are for; it is not busywork.

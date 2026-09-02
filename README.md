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
| **Equalizer** | Ten ISO-octave bands at ±12 dB, eight presets, curve preview, automatic pre-amp headroom so boosts do not clip. |
| **Device** | Which A2DP device is connected, bind one as "my Buzz X9", auto-arm so the curve only goes live for those buds, battery if the stack will say, and the gesture cheat-sheet from the manual. |
| **Probe** | Test tone diagnostic, plus a read-only BLE/SDP dump of what the earbuds advertise. |

### Why the Probe tab exists

Budget TWS are built on chipsets that usually *do* have a control protocol even when the
brand ships no app — JieLi's RCSP over a `0xAE00`-range BLE service or over classic SPP,
Airoha's RACE channel, and so on. The probe dumps the buds' GATT table and SDP records and
flags anything matching a known vendor signature. If something turns up, real on-device EQ
and gesture remapping become buildable. If nothing does, phone-side EQ is the ceiling and
you will know for certain rather than guessing.

The probe only ever reads. It never writes to the earbuds.

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
  audio/Bands.kt           the ten crossover frequencies
  audio/Presets.kt         preset curves
  audio/EqRepo.kt          settings state, persisted to SharedPreferences
  audio/EqEngine.kt        DynamicsProcessing chain, legacy Equalizer fallback
  audio/TestTone.kt        AudioTrack sine generator for the diagnostic
  bt/BtMonitor.kt          A2DP connection state, hidden-API battery read
  bt/BtEventReceiver.kt    restarts the service on boot and on ACL connect
  bt/GattProbe.kt          BLE scan, GATT dump, SDP dump, vendor fingerprints
  service/EqService.kt     foreground service that owns the effect chain
  ui/                      the three screens plus the theme
```

The effect chain has to be owned by something long-lived, because Android releases an
`AudioEffect` as soon as its creating object is collected. That is what the foreground
service and its persistent notification are for; it is not busywork.

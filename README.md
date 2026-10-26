# Needle-Android

A native Android application dedicated to **Cactus Compute Needle only**. The first build targets Android ARM64 devices, including the Vivo Y28 5G.

## What it does

- Downloads the official Needle Android ARM64 static engine and official `needle3.cact` model during CI, then bundles them into the APK.
- Loads the model locally; after installation, inference is intended to work without network access.
- Provides a minimal UI to submit a request and inspect Needle's structured tool-call output.
- Uses GitHub Actions to compile the Android app and inspect the resulting APK.

## Important scope note

Needle is a compact tool-calling / structured-output model, **not a general-purpose free-text chatbot**. This initial app displays the model's tool-call output; it does not execute device actions or perform calculations itself. Treat outputs as data, not as trusted instructions. Any future app-side tools must validate inputs and require confirmation before sensitive actions.

## Build and download

Open **Actions → Build Needle Android APK**. On success, download the `Needle-Android-debug-apk` artifact and install the APK on an ARM64 Android device.

The workflow downloads pinned upstream engine files and the model from the Cactus Compute Hugging Face repository. The model is bundled, so the installed app does not need to download model weights on first launch.

## Offline verification

CI build success and APK structure checks do **not** prove inference works on every physical phone. Install and test on a device, then enable airplane mode and repeat a request to verify offline inference. Runtime inference, memory use and speed still require physical-device testing.

## Upstream

- Source and documentation: https://github.com/cactus-compute/needle
- Official Android ARM64 engine/model distribution: https://huggingface.co/Cactus-Compute/needle3
- Upstream license: Apache-2.0. See upstream repository and model card for current notices.

This is an independent Android wrapper, not an official Cactus Compute application.

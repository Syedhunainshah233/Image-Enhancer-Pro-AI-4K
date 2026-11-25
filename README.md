# Image Enhancer Pro – AI 4K

Android app by **SyedTechdevelopers**: one tap turns a blurry, old or low-quality photo into a sharp 4K image.
Everything runs on the phone (no server): Real-ESRGAN (general x4v3) for blur/noise and 4x upscaling,
GFPGAN v1.4 for faces, ML Kit for face detection.

- 5 free enhances every 24 hours, 5 rewarded ads = 1 hour unlimited
- Every enhance starts after a rewarded ad is watched to the end
- AdMob: app open, banner, native, interstitial, rewarded, rewarded interstitial

## Build
GitHub Actions builds on every push to `main`. Download the APK from the
[latest release](../../releases/tag/latest). The AI models are made by `tools/export_models.py` from the official weights (too big for git)
and packed into an install-time asset pack during the build.

Release builds (real ads + Play Store `.aab`) need two repository secrets:
`IEP_KEYSTORE_BASE64` and `IEP_KEYSTORE_PASSWORD`.

Model licences: Real-ESRGAN (BSD-3-Clause), GFPGAN (Apache-2.0).

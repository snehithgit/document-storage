# Third-party scanner attribution

The `app/src/main/java/com/docuscan/app/scan/Cleanup.kt` and
`BitmapUtil.kt` sources were copied from
https://github.com/codegeasse1/DocuScan
(MIT License, Copyright (c) 2026 codegeasse1).

DocuScan's cleanup and corner detection pipeline identifies its earlier source
as MakeACopy (Apache License 2.0). The relevant original source and notices are
available from https://github.com/codegeasse1/DocuScan.
Both licenses require attribution/notice preservation where applicable.

MIT License
Copyright (c) 2026 codegeasse1

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.

## Phase 2: MakeACopy DocQuadNet-256 and ONNX Runtime

- **Document corner model:** `docquad/docquadnet256_trained_opset17.ort` from
  [egdels/makeacopy](https://github.com/egdels/makeacopy),
  Copyright 2026 Christian Kierdorf, **Apache License 2.0**.
  Pinned upstream Git blob SHA-1: `1cfaaf7ff3fdc04c29749f62051e961fe60accdc`.
  The model is downloaded *during build* by GitHub Actions or
  `bash scripts/fetch_docquad_model.sh`, hash checked and bundled locally into the APK.
  The installed app never requests network permission or downloads weights.
- **Model preprocessing:** 256x256 black letterbox, RGB float32 NCHW, four 64x64 heatmaps,
  deterministic postprocessing, and convexity/area validation.
- **ONNX Runtime Android:** `com.microsoft.onnxruntime:onnxruntime-android:1.24.1`,
  Microsoft, **MIT License**. Published AAR approximately 39 MB before ABI filtering/compression;
  the app's ABI filter includes ARM architectures only.
- **OpenCV:** existing `org.opencv:opencv:4.10.0`, **Apache License 2.0**.
- **Fallback:** existing `Cleanup.detectCorners` multi-pass contour/Hough pipeline,
  used if model is unavailable or yields a low-confidence quad.
- Expect additional APK size due to ONNX Runtime native libraries and the offline model.


# QuPath FastSlide Extension

A [QuPath](https://qupath.github.io) extension that reads whole-slide images using [FastSlide](https://github.com/aiforoncology/fastslide).

Supports brightfield (RGB) and fluorescence/spectral (multi-channel) slides across all formats FastSlide handles:
`.svs`, `.tiff`, `.ome.tiff`, `.czi`, `.mrxs`, `.qptiff`, `.ndpi`, `.scn`, `.bif`, `.isyntax`, `.i2syntax`.

Works on macOS (arm64/x86_64), Linux (x86_64/aarch64), and Windows (x86_64).

## Install

Download `qupath-extension-fastslide-fat-<version>.jar` from the [Releases](https://github.com/aiforoncology/qupath-extension-fastslide/releases) page and drag it onto QuPath, or place it in QuPath's extensions directory.

## Build

### Prerequisites

- Java 21+ JDK

The FastSlide Java API and native libraries are **not** vendored in this repo.
They are resolved from the [FastSlide GitHub Releases](https://github.com/NKI-AI/fastslide/releases)
as ordinary Gradle dependencies (`dev.aifo:fastslide-java` and
`dev.aifo:fastslide-native:<os>-<arch>`) via an anonymous `ivy`/url repository
declared in [`build.gradle.kts`](build.gradle.kts). No token is required.

### Build the fat JAR

```bash
./gradlew fatJar
```

The output is at `build/libs/qupath-extension-fastslide-fat-<version>.jar`. This
single JAR bundles the extension code, the FastSlide Java API, and the native
libraries for every supported platform. Drop it into QuPath's extensions
directory.

To target a different FastSlide release, bump `fastslideVersion` in
[`build.gradle.kts`](build.gradle.kts).

### Building against a local (unpublished) FastSlide

To test against FastSlide JARs you built yourself, stage a local release in the
FastSlide repo and point Gradle at it -- no GitHub round-trip needed:

```bash
# In the FastSlide repo: build + stage a local file:// release.
python3 tools/build_java_artifacts.py --platform darwin_aarch64   # your host
python3 tools/publish_java_artifacts.py --dest local --out-dir /tmp/fastslide-release

# Back here: resolve from the local release instead of GitHub.
./gradlew fatJar -PfastslideRepoUrl=file:///tmp/fastslide-release
```

The `-PfastslideRepoUrl` override accepts any base URL laid out as
`<base>/<version>/<file>`. See the FastSlide docs guide *Java Packages and
Releases* for how releases are produced and published.

## License

See [LICENSE](LICENSE) for details.

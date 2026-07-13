# Getting Started

## Requirements

- **Java 21** or newer (a JDK, not just a JRE).
- A **StarMade installation** — SMEdit reads block definitions and native
  libraries from it, and reads/writes blueprints there.
- 4 GB RAM recommended.

## Build and run

From a clone of the repository:

```bash
./gradlew run
```

To build a runnable jar instead:

```bash
./gradlew build
# produces build/libs/SMEdit-<version>.jar
```

!!! tip "GPU selection"
    On Linux laptops with hybrid graphics (Intel + NVIDIA/AMD), SMEdit
    automatically re-launches on the **discrete GPU**. Pass `-software` to force
    the software renderer, or `-igpu` to stay on the integrated GPU.

## First launch

1. On first run, SMEdit shows a small **options screen**.

    <!-- SCREENSHOT: options screen on first launch -->
    ![Options screen](images/options-screen.png)

2. Set your **StarMade game folder** (the folder that contains `StarMade.jar`).
   Use the **Browse…** button, or let SMEdit auto-detect common install
   locations.
3. Click **Start SMEdit** to open the main editor window.

    <!-- SCREENSHOT: main editor window -->
    ![Main editor window](images/main-window.png)

## Next steps

- [Opening a blueprint](tutorials/opening-a-blueprint.md)

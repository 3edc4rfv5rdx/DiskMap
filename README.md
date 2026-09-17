# DiskMap

**See what takes up the space on your Android phone, and remove what is not needed.**

![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/DiskMap)](https://github.com/3edc4rfv5rdx/DiskMap/releases/latest)

DiskMap scans the phone's storage and shows every folder's size. There are four
views: rings, tiles, a sorted list and the largest files. You can go into any
folder and delete what is not needed, safely through the app's own trash or for
good.

## Features

- **Four views**, switched from the chart button in the top bar:
  - **Rings**: a sunburst chart with two rings around the centre. Larger segments
    show their size next to a dot, and a colour-coded list sits beneath the chart.
  - **Tiles**: a treemap in which each item's area is proportional to its size.
  - **List**: items sorted by size, each with a bar and a percentage.
  - **Largest files**: the 25 largest files anywhere under the current folder, each
    with the folder it sits in.
- **Navigation**:
  - tap a folder to open it;
  - to go back up, use the up arrow or any folder in the path line, tap the centre
    of the rings, or press Back.
- **Selection**: press and hold an item to select it. While anything is selected, a
  tap adds or removes items, so several can be deleted at once.
- **View a file**: tap its icon in the list, or the eye button when one file is
  selected. The file opens in whatever app handles its type.
- **Delete or trash**: a selection gets three buttons: *Delete* (for good, after a
  short confirmation), *Cancel* and *To trash*.
- **Trash**: `Documents/DiskMap/.Trash` on the same storage, so moving an item there is
  instant. Open the trash with the bin button in the top bar. Each entry's ⋮ menu
  restores the item or deletes it for good, and one button empties the whole trash.
- **Internal storage and SD card**: pick one in Settings; the choice appears only
  when there is more than one. Every launch opens the internal storage.
- **⋮ menu**: Rescan, Settings and About.
- **Settings**: light/dark theme, accent colour, language (English, Русский,
  Українська) and an update check.
- **Private**: no account, no ads, no analytics. The app goes online only to check its
  own GitHub release for a newer version.

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/DiskMap/releases/latest):

`diskmap-<version>-arm64-v8a.apk` runs on almost every modern phone.

On first launch the app asks for **All files access**. Without it, the app cannot
measure folders or delete from them.

> Android 11+ does not let any app into `Android/data` and `Android/obb`, so those
> folders show as empty.

## Build

```bash
./00-MakeAll.sh     # signed release build, installed on the emulator and the phone
./06-Test.sh        # unit tests
```

Requires JDK 21 and the Android SDK (compileSdk 36). Release signing reads
`~/.my-safe/key.properties`. Without it the build still works but the APK is unsigned.

The in-app updater and the About dialog are compiled from the sibling folders
`../updater` and `../about`. Clone them next to this repository.

## License

[GNU General Public License v3.0](LICENSE).

## Note

This codebase was developed with the help of artificial intelligence tools.

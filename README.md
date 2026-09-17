# DiskMap

**See what takes up the space on your Android phone, and remove what is not needed.**

![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/DiskMap)](https://github.com/3edc4rfv5rdx/DiskMap/releases/latest)

DiskMap scans the phone's storage and shows every folder's size. There are three
views: rings, tiles and a sorted list. You can go into any folder and delete what
is not needed, safely through the app's own trash or for good.

## Features

- **Rings**: a sunburst chart three levels deep, with a colour-coded list beneath it.
- **Tiles**: a treemap in which each item's area is proportional to its size.
- **List**: sorted by size, with a bar and a percentage for each item.
- **Navigation**:
  - tap a folder to open it;
  - to go back up, tap the centre of the rings, tap any folder in the path line, or press Back;
  - press and hold an item to select it; while anything is selected, a tap adds or
    removes items, so several can be deleted at once.
- **View a file**: tap its icon in the list, or the eye button when one file is
  selected; it opens in whatever app handles its type.
- **Delete or trash**: a selected item gets three buttons: *Delete* (for good, after a
  short confirmation), *Cancel* and *To trash*. The trash is `Documents/DiskMap/.Trash`
  on the same storage, so the move is instant and can be undone from the trash screen,
  opened by the bin button in the top bar.
- **Internal storage and SD card**: pick one in Settings (shown when there is more
  than one); every launch opens the internal storage.
- **Settings**: light/dark theme, accent colour, language (English, Русский,
  Українська) and an update check.
- **Private**: no account, no ads, no analytics. The app goes online only to check its
  own GitHub release for a newer version.

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/DiskMap/releases/latest):

| File | For |
|---|---|
| `diskmap-<version>-arm64-v8a.apk` | almost every modern phone |
| `diskmap-<version>-x86_64.apk` | emulators |
| `diskmap-<version>.apk` | any device (universal) |

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

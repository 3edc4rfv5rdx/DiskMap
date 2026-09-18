# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- F: Tile labels stay white on the blues and the darker colours; black goes only on the bright ones
- E: Tile labels are black on the bright colours, not white on every tile, whichever reads better
- E: The action bar sits right above the navigation bar, no longer a second bar's height higher, and the list above it gets the room
- E: Grant access no longer crashes a device without the all-files screen: it falls back to the app's settings page, then says the access cannot be granted here
- N: The app shows on the Android TV home screen, with a banner of its own
- N: The app installs on Android 11 and up, not 13 and up, so a TV box like the TOX3 takes it; below 13 the app keeps the chosen language itself
- I: Releases carry an armeabi-v7a APK for 32-bit ARM phones and TV boxes
## v0.2.25 (2026-09-17)
- I: Lint skips the shared updater and About modules, the newer-version checks and the v26 icon folder
- R: The unused "close" string is gone
- F: Backups and device transfers are ruled out through dataExtractionRules as well, which Android 12 and later read
- N: Duplicates under the current folder, from the ⋮ menu: matched by size and content, with Keep one of each and a guard that leaves every group a copy
- N: A fourth view lists the 25 largest files anywhere under the current folder, with the folder each sits in; it is not remembered, and the next launch opens the rings
## v0.1.21 (2026-09-17)
- I: The README describes the current features and names only the arm64 APK a release carries
- F: The rings chart is two rings deep and larger, and the top bar, path line and bottom bar take less height
- F: The trash moved to Documents/DiskMap/.Trash on each storage; an old .DiskMapTrash is moved there on the next scan
- R: The chart dimming and outline, the file-count label and the ⋮ button are defined once in ui/Common.kt
- F: Each trash entry keeps Restore and Delete forever behind a ⋮ button instead of two buttons under it
- F: Drop-down menus stand out from the screen: a lighter, well-rounded card with an outline and a shadow
- F: The trash opens from a bin button in the top bar; the storage is picked in Settings, and every launch opens the internal one
- N: A file opens in its viewer app from its icon in the list, or from the eye button when it is the only one selected
- N: Ring segments show their size beside a dot in the middle of the arc, on a plate so it reads over the neighbours
- F: Selected items stand out: a check, bold name and accent background in the list, a thicker outline in the charts
- N: Several files and folders can be selected and deleted or trashed at once
- F: A selected item gets Delete, Cancel and To trash buttons; only Delete asks first, and the trash switch dialog is gone
- F: Cancelling the delete dialog also clears the selection
- F: Larger text in the bottom bar: the selected name, its size and the gesture hint
- F: The bottom bar keeps one height, so the chart no longer jumps when an item is selected or let go
- F: The delete dialog keeps its size when the trash switch is flipped
- F: The colour marks beside list rows are twice as wide
- F: An up-arrow button at the start of the path line goes one folder up
- F: The path line starts with ~ instead of repeating the storage name the top bar already shows
- I: 12-PhoneRELEASE.sh installs on the phone again: the early exit from the template is gone
- F: The chart type is picked from a bar-chart button menu in the top bar instead of a button row above the chart
- I: gradlew.bat is gone: the project is built on Linux only
- N: Settings under the ⋮ menu: theme, accent colour, language and the start-up update check, as in the other apps
- N: Deleting asks whether to move to the app's own trash or delete for good; the trash screen restores, deletes and empties
- N: Folder sizes as rings, tiles or a sorted list, tapping into subfolders and back up through the path line
- I: The shared updater and About dialog are wired in, with 23-ToUpdate.sh to publish the manifest
- I: DiskMap started from the XXX build template
- I: The version is major.minor.build — the date left it — so the tag is v0.1.1 and an artifact <project>-0.1.1-arm64-v8a.apk, each number written once; 20-MakeTag.sh puts the build date after the tag in the CHANGELOG heading, for the reader
- E: The version line looks only at release tags, so a tag like "duplex" can no longer answer which line the last release went out on
- I: Lint reruns instead of reprinting an up-to-date report, and says when the report was written
- I: The previous run's test results are cleared before a run, and a failed test prints its name and the first lines of its message
- I: The clean-tree check refuses an untracked file too, so nothing can go into the APK without going into the tag
- I: The .apkx helper is 99-CopyToAPKX.sh, the number it carries in the older projects
- I: One CHANGELOG legend across every project here — N/E/F/R/I, newest on top, the type letter always followed by a colon
- I: One name for every artifact: `<project>-<version>-<build>-<abi>.apk`, and the tag it goes out under is `v<version>-<build>`.
- I: The build scripts are in place: 97-InitProject.sh names a copy of the template after the folder it is in, and the blue the launcher plate is painted in is named once, in 99-project.conf.

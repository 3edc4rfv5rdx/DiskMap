# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
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

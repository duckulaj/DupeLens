# DupeLens

DupeLens is a local-first Spring Boot application inspired by `fdupes`, with a safer visual workflow and media-aware matching.

## What is included

- Exact duplicate detection using **size → partial SHA-256 → full SHA-256**.
- Persistent SQLite cache so unchanged files do not need to be re-hashed on later scans.
- Similar-image detection using 64-bit perceptual dHash and locality-sensitive candidate bucketing.
- Similar/transcoded-video detection using `ffprobe` duration plus five sampled `ffmpeg` frame hashes.
- Local AI photo grouping through **Ollama**, defaulting to `gemma3:4b`.
- AI category, scene, subjects, keywords and summary metadata stored locally in SQLite.
- Multiple scan roots, recursive/non-recursive scanning, exclusions, min/max file-size filters, hidden-file option, scan history and live progress.
- Pause, resume and cancel controls (pause takes effect after the currently processed file/tool call).
- Thumbnail and media preview UI.
- Preferred keeper folders for exact duplicate groups.
- Suggested exact-duplicate selection.
- Manual selection for similar media.
- Bulk move action.
- **Trash-first deletion via `gio`**. DupeLens deliberately does not fall back to permanent deletion.
- Open containing folder through `xdg-open`.
- Settings UI for thresholds, Ollama model/URL, preferred locations and exclusions.
- Spring Boot Actuator health/metrics endpoints.

## Requirements

- Linux Mint / Ubuntu / Debian is the primary target.
- Java **21 or newer**. Java 25 works.
- Maven is optional: `./mvnw` downloads the current Maven 3.x release if `mvn` is not installed.
- `ffmpeg` + `ffprobe` for similar-video analysis and video thumbnails.
- `gio` for safe Trash operations.
- `xdg-open` for opening folders.
- Ollama for AI photo grouping.

Check your system:

```bash
./check-environment.sh
```

Install the Linux media/desktop helpers:

```bash
./install-media-tools.sh
```

## Open in VS Code

1. Extract the ZIP.
2. In a terminal:

```bash
cd DupeLens
code .
```

3. VS Code will recommend the Java/Spring extensions included in `.vscode/extensions.json`.
4. You can press **F5** and choose **Run DupeLens**, or start the app with:

```bash
./run.sh
```

5. Open:

```text
http://localhost:8080
```

You can also run `DupeLensApplication.java` directly from VS Code after Maven dependencies have loaded.

## Ollama AI setup

DupeLens defaults to:

```text
http://127.0.0.1:11434
gemma3:4b
```

Confirm Ollama is running:

```bash
ollama list
```

If the model is not installed:

```bash
ollama pull gemma3:4b
```

Any compatible local vision model can be selected on **Settings → Local AI**. AI grouping can also be disabled per scan.

### AI privacy behaviour

Images are sent only to the configured Ollama URL. With the default URL they remain on the local machine. The prompt asks the model to provide visual organisation labels while avoiding identity guesses and sensitive personal-attribute inference.

## Matching behaviour

### Exact files

DupeLens first groups by file size. Only same-sized candidates receive a partial SHA-256 hash. Only matching partial candidates receive a complete SHA-256 hash. Files are considered exact duplicates only when the full SHA-256 matches.

Only these exact groups receive automatic removal suggestions.

### Similar photos

Images are resized to a small greyscale representation and given a 64-bit difference hash. Candidate lookup uses 16 small hash bands before calculating Hamming distance. The default threshold is 8 and is configurable.

Similar photos are **never** automatically selected for deletion.

### Similar videos

DupeLens uses `ffprobe` for duration and `ffmpeg` to sample frames at approximately 10%, 30%, 50%, 70% and 90% of the video. Frame perceptual hashes are compared together with a configurable duration tolerance.

This can identify many re-encoded/resized copies, but it is intentionally a review aid rather than a destructive rule.

Similar videos are **never** automatically selected for deletion.

## Keeper selection

For exact duplicates, DupeLens chooses a suggested KEEP copy in this order:

The keeper strategy is configurable under **Settings**. Choose **Preferred folders**, **Oldest**, **Newest**, or **Shortest path**. Preferred-folder mode uses the folder priority list first, then age and path length as tie-breakers.

All other exact copies in the group are preselected, but you still control the final Trash/Move action.

## Database

The local database is:

```text
data/dupelens.db
```

It stores scan history, paths, hashes, perceptual hashes, video fingerprints and AI labels. Deleting the database resets all history and caches; it does not touch scanned files.

## Build a JAR

```bash
./build.sh
java -jar target/dupelens-0.1.0.jar
```

## Useful endpoints

- `/` dashboard
- `/results` results
- `/settings` settings and integration status
- `/history` scan-history summaries
- `/actuator/health` Spring Boot health
- `/actuator/metrics` metrics

## Safety notes

- DupeLens does not permanently delete files from the UI.
- Trash actions require `gio`; if it is missing, the action fails safely.
- Similar photos/videos are not proof of identity and are never preselected for deletion.
- Review suggested exact duplicates before moving them to Trash.
- A backup is still recommended before large cleanup operations.

## Current format notes

Java ImageIO handles common JPEG/PNG/GIF/BMP images. Some formats such as HEIC/AVIF may need a future decoder/plugin for image similarity and thumbnails; they can still participate in exact byte-duplicate detection.

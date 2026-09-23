# Build & Environment Instructions
- Crave CLI is in PATH (`crave.exe`). Always pass `-n` to avoid update prompt hangs.
- Never run heavy Android/AOSP/ROM or multi-hour compilation locally.
- Run builds remotely on Crave: `crave.exe -n run -- "<commands>"`
- Pull results locally: `crave.exe -n pull <path>`

# Agent Directives & Build Instructions

## Cloud Builds & Compilation Policy: Crave Builder
- **Never Build Heavy Targets Locally**: Do not attempt to run heavy compilations, Android/AOSP builds, kernel builds, large image creations, or resource-intensive tasks on the local Windows machine.
- **Use Crave Cloud Builder**: Crave is configured system-wide with `crave.conf` connected to `foss.crave.io`.
- **Command Syntax**:
  - Remote build: `crave.exe -n run -- "<build commands>"`
  - Devspace shell: `crave.exe -n devspace`
  - Pull artifacts locally: `crave.exe -n pull <remote_path>`
- **No Stalling on Prebuilts**: If prebuilt binaries or images are unavailable, compile them directly on Crave instead of stalling.

# Minecraft version ports

The main project targets Minecraft 26.3. Each folder here is an independent build for its exact Minecraft version, with separate Fabric and NeoForge outputs. Each port stores only how it differs from the shared sources: unified diffs in `ports/<version>/patches/{java,resources}/<path>.patch` are applied to a generated copy of `common/src/client` by `ports/patching.gradle`, and `ports/<version>/common/src/client` holds only files that exist in that port alone. A build fails with the patch name when the shared source changed under a patch; fix it by editing the generated file under the port's `build/generated` folder and running `python scripts/make-port-patch.py <version> <dir with java/ and resources/>` (it rewrites the patches for every file that differs from `common/src/client`).

Build one version from the repository root:

```powershell
.\gradlew.bat -p ports/26.2 collectMods
```

Build all four ports and collect their JARs under `build/mods/<minecraft-version>/`:

```powershell
.\scripts\build-ports.ps1
```

On Linux or CI use `scripts/build-ports.sh [version ...]` instead. Both scripts finish with `scripts/verify-port-jars.py`, which checks JAR metadata and bytecode levels.

To build only selected versions:

```powershell
.\scripts\build-ports.ps1 -MinecraftVersions 1.21.1,1.20.1
```

Use the JAR for the exact Minecraft version and loader. Minecraft 26.2 uses Java 25; 1.21.11 and 1.21.1 use Java 21; 1.20.1 uses Java 17. NeoForge 1.20.1 uses the legacy `net.neoforged:forge` fork and Forge-compatible API namespaces, rather than the modern NeoForge API.

Appearance and profile JSON formats are shared across these ports. Resource-pack theme JSON is shared too, but each Minecraft version needs an appropriate `pack.mcmeta` resource-pack format.

The builds do not launch Minecraft. Compilation and archive checks do not replace testing in the matching game version.

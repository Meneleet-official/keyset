param(
    [ValidateSet('26.2', '1.21.11', '1.21.1', '1.20.1')]
    [string[]]$MinecraftVersions = @('26.2', '1.21.11', '1.21.1', '1.20.1')
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repositoryRoot 'gradlew.bat'
foreach ($minecraftVersion in $MinecraftVersions) {
    $projectDirectory = Join-Path $repositoryRoot "ports/$minecraftVersion"
    # collectMods builds only the Fabric and NeoForge artifacts (no test task).
    & $wrapper -p $projectDirectory collectMods --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Keyset build failed for Minecraft $minecraftVersion" }
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'build/mods') -Filter '*.jar' -File)
    if ($jars.Count -lt 2) { throw "Missing Fabric or NeoForge JAR for Minecraft $minecraftVersion" }
    $destination = Join-Path $repositoryRoot "build/mods/$minecraftVersion"
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    foreach ($jar in $jars) { Copy-Item -LiteralPath $jar.FullName -Destination $destination -Force }
}

# Same JAR metadata checks CI runs; they do not start Minecraft.
& python (Join-Path $PSScriptRoot 'verify-port-jars.py') @MinecraftVersions
if ($LASTEXITCODE -ne 0) { throw 'Port JAR verification failed' }

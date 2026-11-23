param([switch]$Demo)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:MESSENGER_KEYSTORE_PASSWORD) { throw 'Set MESSENGER_KEYSTORE_PASSWORD.' }
if ($Demo) { java -jar server/target/server-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo }
else { java -jar server/target/server-0.1.0-SNAPSHOT.jar }

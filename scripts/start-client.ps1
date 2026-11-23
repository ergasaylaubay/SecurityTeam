$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:MESSENGER_KEYSTORE_PASSWORD) { throw 'Set MESSENGER_KEYSTORE_PASSWORD.' }
java --module-path client/target/lib --add-modules javafx.controls -cp 'client/target/classes;client/target/lib/*' kz.securityteam.client.MessengerApp

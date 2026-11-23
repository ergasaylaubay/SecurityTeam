$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:MESSENGER_KEYSTORE_PASSWORD) { throw 'Set MESSENGER_KEYSTORE_PASSWORD first (at least 12 characters).' }
if ($env:MESSENGER_KEYSTORE_PASSWORD.Length -lt 12) { throw 'Use at least 12 characters.' }
if (Test-Path secrets/server.p12) { throw 'Existing keys found. Keep them to preserve message history. Setup cancelled.' }
New-Item -ItemType Directory -Force secrets | Out-Null
$javaSettings = & java -XshowSettings:properties -version 2>&1
$javaHomeLine = $javaSettings | Select-String 'java.home ='
$jdkHome = ($javaHomeLine.ToString() -split '=',2)[1].Trim()
$keytoolPath = Join-Path $jdkHome 'bin/keytool.exe'
function Invoke-Keytool { & $keytoolPath @args; if ($LASTEXITCODE -ne 0) { throw 'keytool failed' } }
Invoke-Keytool -genkeypair -alias tls -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -storetype PKCS12 -keystore secrets/server.p12 '-storepass:env' MESSENGER_KEYSTORE_PASSWORD '-keypass:env' MESSENGER_KEYSTORE_PASSWORD
Invoke-Keytool -genkeypair -alias transport -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=messenger-transport' -keystore secrets/server.p12 '-storepass:env' MESSENGER_KEYSTORE_PASSWORD '-keypass:env' MESSENGER_KEYSTORE_PASSWORD
Invoke-Keytool -genseckey -alias storage -keyalg AES -keysize 256 -keystore secrets/server.p12 '-storepass:env' MESSENGER_KEYSTORE_PASSWORD '-keypass:env' MESSENGER_KEYSTORE_PASSWORD
Invoke-Keytool -exportcert -alias tls -keystore secrets/server.p12 '-storepass:env' MESSENGER_KEYSTORE_PASSWORD -file secrets/server.cer
Invoke-Keytool -importcert -noprompt -alias messenger -storetype PKCS12 -keystore secrets/client-trust.p12 '-storepass:env' MESSENGER_KEYSTORE_PASSWORD -file secrets/server.cer
Write-Host 'Local TLS and encryption keys created. Never upload secrets/.'

param (
    [string]$User = "k-rest-proxy",
    [string]$Password = "password",
    [string]$KeystoreFile = "src/main/resources/keystore.p12"
)

# Function to find keytool
function Get-KeytoolPath {
    if ($env:JAVA_HOME) {
        $path = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $path) { return $path }
    }

    # Try to find in PATH
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    # Try common locations
    $commonPaths = @(
        "C:\Program Files\Java\*\bin\keytool.exe",
        "C:\Program Files (x86)\Java\*\bin\keytool.exe"
    )
    
    foreach ($pattern in $commonPaths) {
        $found = Get-ChildItem $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    return $null
}

$keytool = Get-KeytoolPath

if (-not $keytool) {
    Write-Error "Could not find keytool.exe. Please ensure Java is installed and JAVA_HOME is set, or keytool is in your PATH."
    exit 1
}

Write-Host "Using keytool at: $keytool"

# Ensure directory exists
$parentDir = Split-Path $KeystoreFile
if (-not (Test-Path $parentDir)) {
    New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
}

# Remove existing keystore if it exists to avoid "already exists" error or prompt
if (Test-Path $KeystoreFile) {
    Write-Host "Removing existing keystore at $KeystoreFile..."
    Remove-Item $KeystoreFile -Force
}

Write-Host "Generating keystore for User: $User..."

# Construct DName
$dname = "CN=$User, OU=Dev, O=MyOrg, L=MyCity, ST=MyState, C=US"

# Run keytool
& $keytool -genkeypair `
    -alias $User `
    -keyalg RSA `
    -keysize 2048 `
    -storetype PKCS12 `
    -keystore $KeystoreFile `
    -validity 3650 `
    -storepass $Password `
    -keypass $Password `
    -dname $dname

if ($LASTEXITCODE -eq 0) {
    Write-Host "Keystore generated successfully at $KeystoreFile"
} else {
    Write-Error "Failed to generate keystore."
    exit $LASTEXITCODE
}

param(
  [string]$EnvFile = (Join-Path $PSScriptRoot "..\.env")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$resolvedEnvFile = Resolve-Path $EnvFile -ErrorAction SilentlyContinue

if (-not $resolvedEnvFile) {
  throw "Environment file not found: $EnvFile. Copy .env.example to .env and fill in your local or remote service settings."
}

Get-Content $resolvedEnvFile | ForEach-Object {
  $line = $_.Trim()
  if ($line.Length -eq 0 -or $line.StartsWith("#") -or -not $line.Contains("=")) {
    return
  }

  $parts = $line.Split("=", 2)
  $key = $parts[0].Trim()
  $value = $parts[1].Trim()

  if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
    $value = $value.Substring(1, $value.Length - 2)
  }

  [Environment]::SetEnvironmentVariable($key, $value, "Process")
}

if (-not $env:SPRING_DOCKER_COMPOSE_ENABLED) {
  $env:SPRING_DOCKER_COMPOSE_ENABLED = "false"
}

Push-Location $repoRoot
try {
  $maven = Get-Command mvn -ErrorAction SilentlyContinue
  $mavenPath = if ($maven) { $maven.Source } else { $null }
  if (-not $mavenPath) {
    foreach ($mavenHome in @($env:MAVEN_HOME, $env:M2_HOME)) {
      if (-not [string]::IsNullOrWhiteSpace($mavenHome)) {
        $candidate = Join-Path $mavenHome 'bin\mvn.cmd'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
          $mavenPath = $candidate
          break
        }
      }
    }
  }
  if (-not $mavenPath) {
    $wrapper = Get-ChildItem -Path (Join-Path $HOME '.m2\wrapper\dists\apache-maven-3.9.*-bin\*\apache-maven-3.9.*\bin\mvn.cmd') -File -ErrorAction SilentlyContinue |
      Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($wrapper) { $mavenPath = $wrapper.FullName }
  }
  if (-not $mavenPath) {
    throw 'Maven was not found. Install Maven 3.9+ and add mvn to PATH, or set MAVEN_HOME/M2_HOME to its installation directory.'
  }
  & $mavenPath -f backend/pom.xml spring-boot:run
}
finally {
  Pop-Location
}

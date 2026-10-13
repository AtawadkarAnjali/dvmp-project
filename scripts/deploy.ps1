# Deploys target\dvmp.jar to a local folder, restarts it, and checks /actuator/health
param(
  [string]$AppProfile = "dev",
  [int]$Port = 9091,
  [string]$DeployDir = "D:\deploy\dvmp"
)
$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $DeployDir | Out-Null

# stop the previous instance (if something listens on the port)
$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($conn) {
  $procId = ($conn | Select-Object -First 1).OwningProcess
  Write-Host "Stopping previous instance (PID $procId)"
  Stop-Process -Id $procId -Force
  Start-Sleep -Seconds 3
}

Copy-Item "target\dvmp.jar" "$DeployDir\dvmp.jar" -Force

Start-Process -FilePath "java" `
  -ArgumentList @("-jar", "$DeployDir\dvmp.jar", "--server.port=$Port", "--spring.profiles.active=$AppProfile") `
  -WorkingDirectory $DeployDir -WindowStyle Hidden `
  -RedirectStandardOutput "$DeployDir\app.log" -RedirectStandardError "$DeployDir\app.err.log"

# health check (up to ~60 s)
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-RestMethod "http://localhost:$Port/actuator/health"
    if ($r.status -eq "UP") { $ok = $true; break }
  } catch { }
}
if (-not $ok) { Write-Host "Health check FAILED. See $DeployDir\app.err.log"; exit 1 }
Write-Host "Deployed: http://localhost:$Port (profile=$AppProfile)"

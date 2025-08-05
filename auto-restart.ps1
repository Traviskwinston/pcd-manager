# Auto-restart script for PCD Manager development
# Watches Java source files and automatically triggers server restart via DevTools

Write-Host "[START] PCD Manager Auto-Restart Watcher" -ForegroundColor Green
Write-Host "[WATCH] Monitoring: src/main/java/ and src/main/resources/" -ForegroundColor Cyan
Write-Host "[AUTO] Server will restart automatically when you save Java files" -ForegroundColor Yellow
Write-Host "[STOP] Press Ctrl+C to stop watching" -ForegroundColor Red
Write-Host ""

# File watcher setup
$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = "src"
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true

# Watch only Java files and properties files
$watcher.Filter = "*.*"

# Function to trigger restart
function Trigger-Restart {
    param($FileName)
    
    $timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$timestamp] CHANGED: $FileName" -ForegroundColor Cyan
    Write-Host "[$timestamp] COMPILING..." -ForegroundColor Yellow
    
    # Compile the project
    $compileResult = & mvn compile -q 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[$timestamp] SUCCESS: Compilation successful - Server restarting..." -ForegroundColor Green
    } else {
        Write-Host "[$timestamp] ERROR: Compilation failed:" -ForegroundColor Red
        Write-Host $compileResult -ForegroundColor Red
    }
    Write-Host ""
}

# Register event handlers
Register-ObjectEvent -InputObject $watcher -EventName "Changed" -Action {
    $path = $Event.SourceEventArgs.FullPath
    $fileName = Split-Path $path -Leaf
    $extension = [System.IO.Path]::GetExtension($fileName)
    
    # Only process Java files and properties files
    if ($extension -eq ".java" -or $extension -eq ".properties" -or $extension -eq ".xml") {
        # Avoid duplicate events by adding a small delay
        Start-Sleep -Milliseconds 100
        Trigger-Restart -FileName $fileName
    }
} | Out-Null

Write-Host "[READY] File watcher active! Make changes to your Java files..." -ForegroundColor Green

# Keep the script running
try {
    while ($true) {
        Start-Sleep -Seconds 1
    }
} finally {
    # Cleanup
    $watcher.EnableRaisingEvents = $false
    $watcher.Dispose()
    Write-Host "[STOPPED] File watcher stopped." -ForegroundColor Red
} 
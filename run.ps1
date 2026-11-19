param(
    [string]$Mode = "main"
)

# Ensure bin directory exists
if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

# Compile source files
Write-Host "Compiling Process X Java source files..." -ForegroundColor Cyan
javac -cp "bin;lib\*" -d bin (Get-ChildItem -Path "src\processx" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName)

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Compilation successful!`n" -ForegroundColor Green

# Run selected mode
switch ($Mode.ToLower()) {
    "junit" {
        java -jar lib\junit-platform-console-standalone-1.10.2.jar execute --class-path bin --scan-class-path
    }
    "test" {
        java -cp bin processx.Main --test
    }
    # CPU module
    "cpu-red" { java -jar lib\junit-platform-console-standalone-1.10.2.jar execute --class-path bin --select-class processx.cpu.CpuRedTest }
    "cpu-green" { java -cp "bin;lib\*" processx.cpu.CpuGreenTest }
    "cpu-refactor" { java -cp "bin;lib\*" processx.cpu.CpuRefactorTest }

    # Memory module
    "mem-red" { java -jar lib\junit-platform-console-standalone-1.10.2.jar execute --class-path bin --select-class processx.memory.MemoryRedTest }
    "mem-green" { java -cp "bin;lib\*" processx.memory.MemoryGreenTest }
    "mem-refactor" { java -cp "bin;lib\*" processx.memory.MemoryRefactorTest }

    # Sync module
    "sync-red" { java -jar lib\junit-platform-console-standalone-1.10.2.jar execute --class-path bin --select-class processx.sync.SyncRedTest }
    "sync-green" { java -cp "bin;lib\*" processx.sync.SyncGreenTest }
    "sync-refactor" { java -cp "bin;lib\*" processx.sync.SyncRefactorTest }

    # Disk module
    "disk-red" { java -jar lib\junit-platform-console-standalone-1.10.2.jar execute --class-path bin --select-class processx.disk.DiskRedTest }
    "disk-green" { java -cp "bin;lib\*" processx.disk.DiskGreenTest }
    "disk-refactor" { java -cp "bin;lib\*" processx.disk.DiskRefactorTest }

    # Full Agile suite
    "agile" {
        java -cp bin processx.cpu.CpuRedTest
        java -cp bin processx.cpu.CpuGreenTest
        java -cp bin processx.cpu.CpuRefactorTest

        java -cp bin processx.memory.MemoryRedTest
        java -cp bin processx.memory.MemoryGreenTest
        java -cp bin processx.memory.MemoryRefactorTest

        java -cp bin processx.sync.SyncRedTest
        java -cp bin processx.sync.SyncGreenTest
        java -cp bin processx.sync.SyncRefactorTest

        java -cp bin processx.disk.DiskRedTest
        java -cp bin processx.disk.DiskGreenTest
        java -cp bin processx.disk.DiskRefactorTest
    }
    default {
        java -cp bin processx.Main
    }
}

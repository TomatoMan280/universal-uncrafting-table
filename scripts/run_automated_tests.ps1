param(
    [ValidateSet('All', 'Vanilla', 'ClientGui', 'Atm10')]
    [string]$Suite = 'All',
    [string]$Atm10Path = 'C:\Users\cecil\curseforge\minecraft\Instances\All the Mods 10 - ATM10',
    [switch]$SkipClientGui,
    [switch]$SkipAtm10,
    [switch]$InstallToAtm10
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot

$results = [System.Collections.Generic.List[object]]::new()
$overallSw = [System.Diagnostics.Stopwatch]::StartNew()

function Clear-GradleStaleOutputs {
    Remove-Item -Recurse -Force build\classes, build\resources, build\test-results, build\reports, run-gametest\world -ErrorAction SilentlyContinue
}

function Invoke-GradleStep {
    param(
        [string]$Label,
        [string[]]$GradleArgs
    )
    Write-Host ""
    Write-Host "=== [$Label] .\gradlew.bat $($GradleArgs -join ' ') ===" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & .\gradlew.bat @GradleArgs | Out-Default
    $code = $LASTEXITCODE
    $sw.Stop()
    if ($code -ne 0) {
        throw "Step '$Label' failed with exit code $code."
    }
    return [math]::Round($sw.Elapsed.TotalSeconds, 1)
}

function Get-ReportValue {
    param([string]$Path, [string]$Prefix)
    $line = Get-Content $Path | Where-Object { $_.StartsWith($Prefix) } | Select-Object -First 1
    if ($null -eq $line) { return $null }
    return $line.Substring($Prefix.Length).Trim()
}

$atm10TempDir = Join-Path $repoRoot 'run-atm10-test'

try {
    # 1. Vanilla JUnit + 16 GameTests + Mod Jar Build
    if ($Suite -in @('All', 'Vanilla')) {
        Clear-GradleStaleOutputs
        $secs = Invoke-GradleStep -Label 'Vanilla JUnit + GameTests + Build' -GradleArgs @('verifyAllVanilla', '--no-daemon')

        $junitXml = Get-ChildItem build\test-results\test\TEST-*.xml -ErrorAction SilentlyContinue
        $totalJunit = 0
        $failedJunit = 0
        foreach ($xmlFile in $junitXml) {
            [xml]$doc = Get-Content $xmlFile.FullName
            $totalJunit += [int]$doc.testsuite.tests
            $failedJunit += ([int]$doc.testsuite.failures + [int]$doc.testsuite.errors)
        }
        if ($totalJunit -le 0 -or $failedJunit -gt 0) {
            throw "JUnit reported tests=$totalJunit, failures+errors=$failedJunit"
        }

        $vanillaReport = 'run-gametest\uncrafting_test_report.txt'
        if (-not (Test-Path $vanillaReport)) {
            throw "Missing vanilla report: $vanillaReport"
        }
        $vTotal = [int](Get-ReportValue $vanillaReport 'Total registered items tested:')
        $vUncrafted = [int](Get-ReportValue $vanillaReport 'Successfully uncrafted:')
        $vErrors = [int](Get-ReportValue $vanillaReport 'Errors / self-loops:')
        if ($vErrors -ne 0) {
            throw "Vanilla /testuncrafting reported $vErrors error(s)."
        }

        $results.Add([PSCustomObject]@{
            Suite   = 'Vanilla (JUnit + 16 GameTests)'
            Status  = 'PASSED'
            Seconds = $secs
            Details = "JUnit=$totalJunit/$totalJunit, Items=$vTotal, Uncrafted=$vUncrafted, Errors=$vErrors"
        })
    }

    # 2. Automated Client GUI & Render FPS Test
    if (($Suite -eq 'ClientGui') -or ($Suite -eq 'All' -and -not $SkipClientGui)) {
        Remove-Item run\uncrafting_client_gui_report.txt -Force -ErrorAction SilentlyContinue
        Remove-Item -Recurse -Force run\saves\uncrafting_auto_gui_test -ErrorAction SilentlyContinue
        $secs = Invoke-GradleStep -Label 'Client GUI & Render Latency Auto-Test' -GradleArgs @('runClient', '-PclientAutoTest=true', '--no-daemon')
        Remove-Item -Recurse -Force run\saves\uncrafting_auto_gui_test -ErrorAction SilentlyContinue

        $guiReport = 'run\uncrafting_client_gui_report.txt'
        if (-not (Test-Path $guiReport)) {
            throw "Missing client GUI report: $guiReport"
        }
        $guiStatus = Get-ReportValue $guiReport 'STATUS:'
        $guiChecks = Get-ReportValue $guiReport 'Checks Passed:'
        $guiFailCount = [int](Get-ReportValue $guiReport 'Failures:')
        if ($guiStatus -ne 'PASSED' -or $guiFailCount -ne 0) {
            Get-Content $guiReport | ForEach-Object { Write-Host $_ }
            throw "Client GUI auto-test failed (STATUS=$guiStatus, Failures=$guiFailCount)"
        }

        $results.Add([PSCustomObject]@{
            Suite   = 'Client GUI + Cursor FPS'
            Status  = 'PASSED'
            Seconds = $secs
            Details = "ChecksPassed=$guiChecks, Failures=$guiFailCount"
        })
    }

    # 3. Separate ATM10 64k-Item Sweep (auto-run server world sweep)
    if (($Suite -eq 'Atm10') -or ($Suite -eq 'All' -and -not $SkipAtm10)) {
        if (-not (Test-Path $Atm10Path)) {
            if ($Suite -eq 'Atm10') {
                throw "ATM10 instance not found at: $Atm10Path"
            }
            $results.Add([PSCustomObject]@{
                Suite   = 'ATM10 64k Sweep'
                Status  = 'SKIPPED'
                Seconds = 0
                Details = "ATM10 path not present ($Atm10Path)"
            })
        } else {
            Write-Host ""
            Write-Host "=== Staging isolated ATM10 test directory (saves/ untouched) ===" -ForegroundColor Cyan
            Remove-Item -Recurse -Force $atm10TempDir -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path (Join-Path $atm10TempDir 'mods') -Force | Out-Null
            New-Item -ItemType Directory -Path (Join-Path $atm10TempDir 'world') -Force | Out-Null

            $clientOnlyPatterns = @(
                'sodium-neoforge-*',
                'sodium-extra-neoforge-*',
                'iris-neoforge-*',
                'IrisSearch-*',
                'fancymenu_neoforge_*',
                'drippyloadingscreen_neoforge_*',
                'CrashAssistant-neoforge-*',
                'ImmediatelyFast-NeoForge-*',
                'borderless-neoforge-*',
                'moreoverlays-*',
                'colorwheel*',
                'darkmodeeverywhere*',
                'formations*',
                'uncrafting_table-*.jar'
            )

            $atm10Mods = Get-ChildItem (Join-Path $Atm10Path 'mods') -Filter *.jar
            $copiedCount = 0
            foreach ($jar in $atm10Mods) {
                $skip = $false
                foreach ($pat in $clientOnlyPatterns) {
                    if ($jar.Name -like $pat) { $skip = $true; break }
                }
                if (-not $skip) {
                    Copy-Item $jar.FullName (Join-Path $atm10TempDir "mods\$($jar.Name)")
                    $copiedCount++
                }
            }

            foreach ($sub in @('kubejs', 'config', 'defaultconfigs', 'datapacks', 'libraries-integratedscripting')) {
                $srcSub = Join-Path $Atm10Path $sub
                if (Test-Path $srcSub) {
                    Copy-Item $srcSub (Join-Path $atm10TempDir $sub) -Recurse -Force
                }
            }

            "eula=true" | Set-Content (Join-Path $atm10TempDir 'eula.txt') -Encoding ASCII
            @(
                "online-mode=false",
                "level-name=atm10_uncrafting_sweep",
                "level-type=minecraft\:flat",
                "spawn-protection=0",
                "max-tick-time=-1",
                "pause-when-empty-seconds=-1",
                "server-port=25579"
            ) | Set-Content (Join-Path $atm10TempDir 'server.properties') -Encoding ASCII

            $atm10NeoVer = '21.1.251'
            $instanceJson = Join-Path $Atm10Path 'minecraftinstance.json'
            if (Test-Path $instanceJson) {
                try {
                    $meta = Get-Content $instanceJson -Raw | ConvertFrom-Json
                    if ($meta.baseModLoader.forgeVersion) {
                        $atm10NeoVer = $meta.baseModLoader.forgeVersion
                    }
                } catch { }
            }

            Write-Host "Staged $copiedCount ATM10 jars (NeoForge $atm10NeoVer)" -ForegroundColor Gray
            Clear-GradleStaleOutputs

            $atmArgs = @(
                'runServer',
                "-PtestNeoVersion=$atm10NeoVer",
                "-PserverDir=run-atm10-test",
                "-PserverJvm=-Xmx8G",
                '-PskipRuntimeJei=true',
                '-PautoSweep=true',
                '--no-daemon'
            )
            $atmReport = Join-Path $atm10TempDir 'uncrafting_test_report.txt'
            try {
                $secs = Invoke-GradleStep -Label "ATM10 Full Registry Auto-Sweep ($atm10NeoVer)" -GradleArgs $atmArgs
            } catch {
                if (Test-Path $atmReport) {
                    throw
                }
                Write-Host "Retrying ATM10 server boot once (transient 476-mod parallel registration race before sweep started)..." -ForegroundColor Yellow
                Remove-Item -Recurse -Force (Join-Path $atm10TempDir 'world') -ErrorAction SilentlyContinue
                $secs = Invoke-GradleStep -Label "ATM10 Full Registry Auto-Sweep ($atm10NeoVer) [Retry]" -GradleArgs $atmArgs
            }

            if (-not (Test-Path $atmReport)) {
                throw "Missing ATM10 report: $atmReport"
            }
            $aTotal = [int](Get-ReportValue $atmReport 'Total registered items tested:')
            $aUncrafted = [int](Get-ReportValue $atmReport 'Successfully uncrafted:')
            $aMulti = [int](Get-ReportValue $atmReport 'Multi-recipe items detected:')
            $aErrors = [int](Get-ReportValue $atmReport 'Errors / self-loops:')
            if ($aTotal -lt 50000 -or $aErrors -ne 0) {
                Get-Content $atmReport -Tail 40 | ForEach-Object { Write-Host $_ }
                throw "ATM10 sweep assertion failed (Items=$aTotal, Errors=$aErrors)"
            }

            $results.Add([PSCustomObject]@{
                Suite   = "ATM10 64k Auto-Sweep ($atm10NeoVer)"
                Status  = 'PASSED'
                Seconds = $secs
                Details = "Items=$aTotal, Uncrafted=$aUncrafted, Multi=$aMulti, Errors=$aErrors"
            })

            # Restore baseline NeoForge build artifacts cleanly after 21.1.251 ATM10 run
            Clear-GradleStaleOutputs
            $null = Invoke-GradleStep -Label 'Restore Baseline NeoForge Build' -GradleArgs @('jar', 'createClientLaunchScript', '--no-daemon')
        }
    }

    if ($InstallToAtm10) {
        $modVersionLine = Get-Content gradle.properties | Where-Object { $_ -match '^mod_version\s*=' } | Select-Object -First 1
        if (-not $modVersionLine) { throw "Could not determine mod_version from gradle.properties." }
        $modVersion = ($modVersionLine -split '=', 2)[1].Trim()
        $expectedJar = Join-Path $repoRoot "build\libs\uncrafting_table-$modVersion.jar"
        if (-not (Test-Path $expectedJar)) { throw "Expected built jar not found: $expectedJar" }
        $jar = Get-Item $expectedJar
        $destTargets = @($Atm10Path)
        $altAtm10 = 'C:\Users\cecil\curseforge\minecraft\Instances\All the Mods 10 - ATM10 (1)'
        if (Test-Path $altAtm10) { $destTargets += $altAtm10 }
        foreach ($inst in $destTargets) {
            $modsDir = Join-Path $inst 'mods'
            if (Test-Path $modsDir) {
                Get-ChildItem $modsDir -Filter 'uncrafting_table-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
                Copy-Item $jar.FullName (Join-Path $modsDir $jar.Name) -Force
                Write-Host "Installed $($jar.Name) -> $modsDir" -ForegroundColor Green
            }
        }
    }

    $overallSw.Stop()
    Write-Host ""
    Write-Host "====== AUTOMATED TEST SUITE SUMMARY ($([math]::Round($overallSw.Elapsed.TotalSeconds, 1))s) ======" -ForegroundColor Green
    $results | Format-Table -AutoSize
}
finally {
    if (Test-Path $atm10TempDir) {
        Remove-Item -Recurse -Force $atm10TempDir -ErrorAction SilentlyContinue
    }
    Pop-Location
}

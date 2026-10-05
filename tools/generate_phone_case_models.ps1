param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$modelDir = Join-Path $ProjectRoot "src/main/resources/assets/newphone/models/item"
$basePath = Join-Path $modelDir "z_phone_base.json"
$rootPath = Join-Path $modelDir "z_phone.json"
$utf8 = [System.Text.UTF8Encoding]::new($false)

$styles = @(
    @{ Id = "bunny_blush"; Value = 1.0; Bunny = $true; Armor = $false },
    @{ Id = "carbon_guard"; Value = 2.0; Bunny = $false; Armor = $true },
    @{ Id = "forest_leather"; Value = 3.0; Bunny = $false; Armor = $false },
    @{ Id = "midnight_cyber"; Value = 4.0; Bunny = $false; Armor = $true },
    @{ Id = "bunny_block"; Value = 5.0; Bunny = $true; Armor = $false }
)

function Read-Json([string]$path) {
    return Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Write-Json([string]$path, $value) {
    $json = $value | ConvertTo-Json -Depth 100
    [System.IO.File]::WriteAllText($path, $json + [Environment]::NewLine, $utf8)
}

$sourcePath = if (Test-Path $basePath) { $basePath } else { $rootPath }
$baseModel = Read-Json $sourcePath
if ($baseModel.loader -ne "forge:obj") {
    throw "Export the Blender model with tools/export_phone_2_to_forge.py before generating case models."
}
$baseModel.PSObject.Properties.Remove("overrides")
Write-Json $basePath $baseModel


foreach ($style in $styles) {
    $caseModel = Read-Json $basePath
    # All case variants use the actual Blender handset and a fitted OBJ shell.
    $shell = if ($style.Bunny) { "bunny" } elseif ($style.Armor) { "armor" } else { "plain" }
    $caseModel.model = "newphone:models/item/mikasrevs_phone_2_$shell.obj"
    $caseModel.textures.particle = "newphone:item/z_phone_case_$($style.Id)"
    $caseModel.textures | Add-Member -NotePropertyName "case" -NotePropertyValue "newphone:item/z_phone_case_$($style.Id)" -Force
    $caseModel.textures | Add-Member -NotePropertyName "case_accent" -NotePropertyValue "newphone:item/z_phone_case_$($style.Id)_accent" -Force
    $caseModel.textures | Add-Member -NotePropertyName "case_logo" -NotePropertyValue "newphone:item/z_phone_case_$($style.Id)_logo" -Force
    $caseModel.PSObject.Properties.Remove("elements")
    Write-Json (Join-Path $modelDir "z_phone_case_$($style.Id).json") $caseModel

    for ($frame = 1; $frame -le 5; $frame++) {
        $child = [ordered]@{
            parent = "newphone:item/z_phone_case_$($style.Id)"
            textures = [ordered]@{ screen = "newphone:item/z_phone_screen_$frame" }
        }
        Write-Json (Join-Path $modelDir "z_phone_case_$($style.Id)_boot_$frame.json") $child
    }

    $callChild = [ordered]@{
        parent = "newphone:item/z_phone_case_$($style.Id)"
        textures = [ordered]@{ screen = "newphone:item/z_phone_screen_5" }
    }
    Write-Json (Join-Path $modelDir "z_phone_case_$($style.Id)_call.json") $callChild
}

$overrides = [System.Collections.Generic.List[object]]::new()
$bootThresholds = @(0.16, 0.32, 0.5, 0.68, 0.86)
for ($frame = 1; $frame -le 5; $frame++) {
    $overrides.Add([ordered]@{
        predicate = [ordered]@{ "newphone:screen_anim" = $bootThresholds[$frame - 1] }
        model = "newphone:item/z_phone_boot_$frame"
    })
}
$overrides.Add([ordered]@{
    predicate = [ordered]@{ "newphone:call_pose" = 1.0 }
    model = "newphone:item/z_phone_call"
})

foreach ($style in $styles) {
    $overrides.Add([ordered]@{
        predicate = [ordered]@{ "newphone:case_style" = $style.Value }
        model = "newphone:item/z_phone_case_$($style.Id)"
    })
    for ($frame = 1; $frame -le 5; $frame++) {
        $overrides.Add([ordered]@{
            predicate = [ordered]@{
                "newphone:case_style" = $style.Value
                "newphone:screen_anim" = $bootThresholds[$frame - 1]
            }
            model = "newphone:item/z_phone_case_$($style.Id)_boot_$frame"
        })
    }
    $overrides.Add([ordered]@{
        predicate = [ordered]@{
            "newphone:case_style" = $style.Value
            "newphone:call_pose" = 1.0
        }
        model = "newphone:item/z_phone_case_$($style.Id)_call"
    })
}

$dispatcher = [ordered]@{
    parent = "newphone:item/z_phone_base"
    overrides = $overrides.ToArray()
}
Write-Json $rootPath $dispatcher

Write-Host "Generated $($styles.Count) case model families and the phone model dispatcher."
& (Join-Path $PSScriptRoot "generate_phone_cases.ps1") -ProjectRoot $ProjectRoot
& (Join-Path $PSScriptRoot "fit_phone_inventory_models.ps1") -ProjectRoot $ProjectRoot

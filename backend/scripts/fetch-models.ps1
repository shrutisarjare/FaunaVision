<#
.SYNOPSIS
    Downloads the ONNX models used by the /identify-* endpoints.

.DESCRIPTION
    The models are large (about 160 MB in total) and are not committed to the
    repository. They are read at runtime from app.ml.model-dir, which defaults
    to <repo>/backend/models.

    CLIP (clip-vit-base-patch32) is what actually matches an upload against the
    species catalogue. MobileNetV2 and YAMNet are kept as fallbacks: the API
    silently uses them when the CLIP files are absent.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/fetch-models.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/fetch-models.ps1 -Destination D:\models -Force
#>
[CmdletBinding()]
param(
    [string]$Destination,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# $PSScriptRoot is empty while param() defaults are evaluated, so resolve it here.
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $scriptRoot = if ($PSScriptRoot) { $PSScriptRoot }
                  elseif ($MyInvocation.MyCommand.Path) { Split-Path -Parent $MyInvocation.MyCommand.Path }
                  else { (Get-Location).Path }
    $Destination = Join-Path $scriptRoot '..\models'
}

$clip = 'https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main'

$models = @(
    @{
        Name = 'vision_model.onnx'
        Dir  = 'clip'
        Url  = "$clip/onnx/vision_model_quantized.onnx"
    },
    @{
        Name = 'text_model.onnx'
        Dir  = 'clip'
        Url  = "$clip/onnx/text_model_quantized.onnx"
    },
    @{
        Name = 'vocab.json'
        Dir  = 'clip'
        Url  = "$clip/vocab.json"
    },
    @{
        Name = 'merges.txt'
        Dir  = 'clip'
        Url  = "$clip/merges.txt"
    },
    @{
        Name = 'mobilenetv2-12.onnx'
        Dir  = ''
        Url  = 'https://github.com/onnx/models/raw/main/validated/vision/classification/mobilenet/model/mobilenetv2-12.onnx'
    },
    @{
        Name = 'imagenet-synset.txt'
        Dir  = ''
        Url  = 'https://raw.githubusercontent.com/onnx/models/main/validated/vision/classification/synset.txt'
    },
    @{
        Name = 'yamnet.onnx'
        Dir  = ''
        Url  = 'https://huggingface.co/audiomagic/yamnet-onnx/resolve/main/yamnet.onnx'
    }
)

$target = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path $target | Out-Null

foreach ($model in $models) {
    $dir = if ($model.Dir) { Join-Path $target $model.Dir } else { $target }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $path = Join-Path $dir $model.Name
    $label = if ($model.Dir) { $model.Dir + '/' + $model.Name } else { $model.Name }

    if ((Test-Path $path) -and -not $Force) {
        Write-Host ('present   {0}' -f $label) -ForegroundColor DarkGray
        continue
    }

    Write-Host ('fetching  {0}' -f $label) -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri $model.Url -OutFile $path -UseBasicParsing -MaximumRedirection 5
        $size = (Get-Item $path).Length / 1MB
        Write-Host ('saved     {0} ({1:N1} MB)' -f $label, $size) -ForegroundColor Green
    }
    catch {
        if (Test-Path $path) {
            Remove-Item $path -ErrorAction SilentlyContinue
        }
        Write-Host ('FAILED    {0}: {1}' -f $label, $_.Exception.Message) -ForegroundColor Red
        Write-Host ('          download it manually from {0}' -f $model.Url) -ForegroundColor Yellow
    }
}

Write-Host ''
Write-Host ('Models directory: {0}' -f $target)
Write-Host 'Restart the API after adding or replacing a model.'

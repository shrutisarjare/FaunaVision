# Measures identification accuracy against a fixed set of labelled photos.
#
#   .\scripts\bench-identify.ps1            # uses the running server on port 8090
#   .\scripts\bench-identify.ps1 -Api http://localhost:8080
#
# Photos come from Wikipedia on first run and are cached, so repeated runs are cheap.
# "top-1" is the share of photos where the expected species ranked first; "top-5" is the
# share where it appeared at all, which is what the ranked match list exposes to a user.

param(
    [string] $Api = 'http://localhost:8090',
    [string] $CacheDir = (Join-Path $env:TEMP 'species-bench')
)

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Stop'

New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null
$userAgent = 'EndangeredSpeciesBench/1.0 (https://github.com/shrutisarjare/endangered-species)'

# Wikipedia photo title -> the catalogue name we expect it to resolve to
$cases = @(
    @{ title = 'Bengal tiger';        expect = 'Bengal Tiger' },
    @{ title = 'Indian elephant';     expect = 'Asian Elephant' },
    @{ title = 'Indian peafowl';      expect = 'Indian Peafowl' },
    @{ title = 'Great horned owl';    expect = 'Great Horned Owl' },
    @{ title = 'Indian leopard';      expect = 'Indian Leopard' },
    @{ title = 'Sloth bear';          expect = 'Sloth Bear' },
    @{ title = 'Sambar deer';         expect = 'Sambar Deer' },
    @{ title = 'Giant panda';         expect = 'Giant Panda' },
    @{ title = 'Bald eagle';          expect = 'Bald Eagle' },
    @{ title = 'Grey crowned crane';  expect = 'Red-crowned Crane' },
    @{ title = 'Blue whale';          expect = 'Blue Whale' },
    @{ title = 'Great white shark';   expect = 'Great White Shark' },
    @{ title = 'Snow leopard';        expect = 'Snow Leopard' },
    @{ title = 'Asiatic lion';        expect = 'Asiatic Lion' },
    @{ title = 'Chital';              expect = 'Chital (Spotted Deer)' },
    @{ title = 'Emperor penguin';     expect = 'Emperor Penguin' },
    @{ title = 'Common ostrich';      expect = 'Ostrich' },
    @{ title = 'Polar bear';          expect = 'Polar Bear' },
    @{ title = 'Plains zebra';        expect = 'Plains Zebra' },
    @{ title = 'Reticulated giraffe'; expect = 'Reticulated Giraffe' },
    @{ title = 'Green sea turtle';    expect = 'Green Sea Turtle' },
    @{ title = 'Grey parrot';         expect = 'African Grey Parrot' }
)

function Save-Photo([string] $title, [string] $file) {
    if (Test-Path $file) { return $file }
    # The Wikipedia API hands out thumbnail URLs that download without a referrer check.
    # curl is used because PowerShell's default headers get the request flagged as a bot.
    $uri = "https://en.wikipedia.org/w/api.php?action=query&format=json&prop=pageimages&pithumbsize=800&titles=$([uri]::EscapeDataString($title))"
    $response = (& curl.exe -s -A $userAgent -m 30 $uri) | ConvertFrom-Json
    $src = ($response.query.pages.PSObject.Properties.Value | Select-Object -First 1).thumbnail.source
    if (-not $src) { throw "no thumbnail for $title" }
    & curl.exe -s -L -A $userAgent -m 60 -o $file $src
    if (-not (Test-Path $file) -or (Get-Item $file).Length -lt 1000) { throw "download failed for $title" }
    $file
}

$rows = foreach ($case in $cases) {
    $file = Join-Path $CacheDir (($case.title -replace '[^\w]', '-') + '.jpg')
    try {
        Save-Photo $case.title $file | Out-Null
    } catch {
        Write-Warning "skipping $($case.title): $($_.Exception.Message)"
        continue
    }

    $started = Get-Date
    $result = (& curl.exe -s -X POST -F "file=@$file" "$Api/identify-image") | ConvertFrom-Json
    $names = @($result.matches | ForEach-Object { $_.name })

    [pscustomobject]@{
        photo  = $case.title
        expect = $case.expect
        top1   = $result.species
        hit    = ($result.species -eq $case.expect)
        top5   = ($names -contains $case.expect)
        ms     = [int]((Get-Date) - $started).TotalMilliseconds
        ranked = (@($result.matches) | ForEach-Object {
                "$($_.name):$([math]::Round($_.confidence * 100, 1))" }) -join ' '
    }
}

$rows | Select-Object photo, expect, top1, hit, top5, ms | Format-Table -AutoSize
"top-1 accuracy: {0}/{1}" -f (@($rows | Where-Object hit).Count), $rows.Count
"top-5 recall:   {0}/{1}" -f (@($rows | Where-Object top5).Count), $rows.Count
$rows | ForEach-Object { "  $($_.expect) -> $($_.ranked)" }

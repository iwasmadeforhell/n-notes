# Builds N.Notes.apk without Gradle. Needs JDK 17 and .sdk\x (build-tools + android.jar, see README).
# Usage: build.ps1 -Version 1.1.0 -Code 2   (Code must increase with every release)
param([string]$Version = '1.1.0', [int]$Code = 2)
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot; $app = "$root\app"; $out = "$root\build"
$bt = (Get-ChildItem "$root\.sdk\x\bt\*" -Directory | Select -First 1).FullName
$jar = (Get-ChildItem "$root\.sdk\x\pl\*\android.jar" | Select -First 1).FullName
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin'
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $out, "$out\cls", "$out\res" | Out-Null
(Get-Content "$app\AndroidManifest.xml" -Raw).Replace('@CODE@', "$Code").Replace('@VERSION@', $Version) | Set-Content "$out\AndroidManifest.xml" -Encoding utf8

# icon: violet "N" + white dot on a dark rounded square (same as the in-app logo)
Add-Type -AssemblyName System.Drawing
function Icon($px, $path) {
  $bmp = New-Object Drawing.Bitmap $px, $px; $g = [Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'; $k = $px / 100.0
  $r = 22 * $k; $gp = New-Object Drawing.Drawing2D.GraphicsPath
  $gp.AddArc(0, 0, $r * 2, $r * 2, 180, 90); $gp.AddArc($px - $r * 2, 0, $r * 2, $r * 2, 270, 90)
  $gp.AddArc($px - $r * 2, $px - $r * 2, $r * 2, $r * 2, 0, 90); $gp.AddArc(0, $px - $r * 2, $r * 2, $r * 2, 90, 90); $gp.CloseFigure()
  $g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(20, 24, 34))), $gp)
  $pen = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(195, 177, 255)), (9 * $k)
  $pen.StartCap = 'Round'; $pen.EndCap = 'Round'; $pen.LineJoin = 'Round'
  $pts = [Drawing.PointF[]]@((New-Object Drawing.PointF (26 * $k), (72 * $k)), (New-Object Drawing.PointF (26 * $k), (28 * $k)), (New-Object Drawing.PointF (58 * $k), (72 * $k)), (New-Object Drawing.PointF (58 * $k), (28 * $k)))
  $g.DrawLines($pen, $pts)
  $g.FillEllipse([Drawing.Brushes]::White, (70 * $k), (64 * $k), (12 * $k), (12 * $k))
  $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
}
foreach ($d in @{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }.GetEnumerator()) {
  New-Item -ItemType Directory "$out\res\mipmap-$($d.Key)" | Out-Null
  Icon $d.Value "$out\res\mipmap-$($d.Key)\ic_launcher.png"
}
Icon 512 "$root\logo.png"

& "$bt\aapt2.exe" compile --dir "$out\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$out\AndroidManifest.xml" -A "$app\assets" --min-sdk-version 24 --target-sdk-version 34 "$out\res.zip"
& "$jdk\javac.exe" -source 8 -target 8 -cp $jar -d "$out\cls" "$app\java\nnotes\Main.java" 2>&1 | Where-Object { $_ -notmatch 'warning|Note:' }
& "$bt\d8.bat" --lib $jar --output $out (Get-ChildItem "$out\cls" -Recurse -Filter *.class).FullName
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zf = [IO.Compression.ZipFile]::Open("$out\base.apk", "Update"); [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zf, "$out\classes.dex", "classes.dex"); $zf.Dispose()
& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
$ks = "$root\release.keystore"
if (-not (Test-Path $ks)) { & "$jdk\keytool.exe" -genkeypair -keystore $ks -alias nnotes -keyalg RSA -keysize 2048 -validity 36500 -storepass nnotes123 -keypass nnotes123 -dname 'CN=N.Notes' }
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:nnotes123 --out "$root\N.Notes.apk" "$out\aligned.apk"
& "$bt\apksigner.bat" verify "$root\N.Notes.apk"
$h = (Get-FileHash "$root\N.Notes.apk" -Algorithm SHA256).Hash.ToLower()
"$h  N.Notes.apk" | Set-Content "$root\N.Notes.apk.sha256" -Encoding ascii
Get-Item "$root\N.Notes.apk" | select Name, Length

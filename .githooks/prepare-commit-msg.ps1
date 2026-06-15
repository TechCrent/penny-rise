# Strip Cursor agent attribution from commit messages (Windows).
param([string]$CommitMsgPath)
if (-not $CommitMsgPath) { exit 0 }
$content = Get-Content -Raw -Path $CommitMsgPath
$content = $content -replace '(?m)^Co-authored-by: Cursor <cursoragent@cursor.com>\r?\n', ''
$content = $content -replace '(?m)^Made with Cursor\r?\n', ''
$content = $content -replace '(?m)^Made-with: Cursor\r?\n', ''
Set-Content -Path $CommitMsgPath -Value $content.TrimEnd() -NoNewline

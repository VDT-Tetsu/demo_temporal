# Tao data test: nhieu tenant voi ket qua khac nhau, tu dong gui approve/reject/retry/abort.
#   .\scripts\seed-test-data.ps1 [-BaseUrl http://localhost:8081] [-MaxMinutes 20]
# Script dung khi moi tenant da toi trang thai muc tieu (hoac het MaxMinutes).
# OnApproval: approve | reject | wait (de nguyen WAITING_APPROVAL)
# OnRetry:    retry   | abort  | wait (de nguyen WAITING_RETRY)
# File luu UTF-8 co BOM de Windows PowerShell 5.1 doc dung tieng Viet.
param(
    [string]$BaseUrl = "http://localhost:8081",
    [int]$MaxMinutes = 20
)

$api = "$BaseUrl/api/tenants"

$plans = @(
    @{ Name = "Công ty Minh Phát";   Plan = "PRO";        FailureRate = 0.0;   OnApproval = "approve"; OnRetry = "retry"; Expect = "SUCCESS" },
    @{ Name = "Sao Mai Logistics";   Plan = "ENTERPRISE"; FailureRate = $null; OnApproval = "approve"; OnRetry = "retry"; Expect = "SUCCESS (lỗi random theo .env)" },
    @{ Name = "Hải Đăng Tech";       Plan = "PRO";        FailureRate = 0.6;   OnApproval = "approve"; OnRetry = "retry"; Expect = "SUCCESS, có thể qua WAITING_RETRY -> retry" },
    @{ Name = "Bình Minh Retail";    Plan = "BASIC";      FailureRate = 0.0;   OnApproval = "reject";  OnRetry = "retry"; Expect = "FAILED (reject + rollback 3 bước)" },
    @{ Name = "Trường An Foods";     Plan = "PRO";        FailureRate = 0.6;   OnApproval = "approve"; OnRetry = "abort"; Expect = "FAILED nếu lỗi (abort + rollback), ngược lại SUCCESS" },
    @{ Name = "Kim Long Steel";      Plan = "ENTERPRISE"; FailureRate = 1.0;   OnApproval = "approve"; OnRetry = "abort"; Expect = "FAILED (abort ngay bước đầu)" },
    @{ Name = "Ngọc Lan Spa";        Plan = "BASIC";      FailureRate = 0.0;   OnApproval = "wait";    OnRetry = "retry"; Expect = "PENDING: WAITING_APPROVAL" },
    @{ Name = "Thiên Long Media";    Plan = "PRO";        FailureRate = 0.0;   OnApproval = "wait";    OnRetry = "retry"; Expect = "PENDING: WAITING_APPROVAL" },
    @{ Name = "Phúc Thịnh Build";    Plan = "ENTERPRISE"; FailureRate = 1.0;   OnApproval = "wait";    OnRetry = "wait";  Expect = "PENDING: WAITING_RETRY" }
)

# Tao sau cung, khong can thiep: luc script ket thuc cac tenant nay van dang chay
$late = @(
    @{ Name = "Hoàng Gia Hotel"; Plan = "PRO";   FailureRate = 0.0 },
    @{ Name = "Đông Á Pharma";   Plan = "BASIC"; FailureRate = 0.3 }
)

function Post($url, $body) {
    $json = if ($body) { $body | ConvertTo-Json } else { "{}" }
    # PowerShell 5.1 mac dinh gui body bang ISO-8859-1 -> phai tu ma hoa UTF-8
    Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

function Slug($name) {
    $n = $name.Normalize([Text.NormalizationForm]::FormD) -replace '\p{M}', '' -replace '[đĐ]', 'd'
    return ($n.ToLower() -replace '[^a-z0-9]', '')
}

function New-Tenant($p) {
    $body = @{ name = $p.Name; adminEmail = "admin@" + (Slug $p.Name) + ".vn"; plan = $p.Plan }
    if ($null -ne $p.FailureRate) { $body.failureRate = $p.FailureRate }
    return (Post $api $body).id
}

foreach ($p in $plans) {
    $p.Id = New-Tenant $p
    $rate = if ($null -eq $p.FailureRate) { "mặc định" } else { $p.FailureRate }
    Write-Host ("Created {0,-20} id={1} failureRate={2,-8} -> {3}" -f $p.Name, $p.Id, $rate, $p.Expect)
}

$deadline = (Get-Date).AddMinutes($MaxMinutes)
while ((Get-Date) -lt $deadline) {
    $pending = 0
    foreach ($p in $plans) {
        $t = Invoke-RestMethod "$api/$($p.Id)"
        $done = $t.status -in @("SUCCESS", "FAILED") -or
                ($t.status -eq "WAITING_APPROVAL" -and $p.OnApproval -eq "wait") -or
                ($t.status -eq "WAITING_RETRY" -and $p.OnRetry -eq "wait")
        if ($done) { continue }
        $pending++

        $action = switch ($t.status) {
            "WAITING_APPROVAL" { $p.OnApproval }
            "WAITING_RETRY"    { $p.OnRetry }
            default            { $null }
        }
        if (-not $action) { continue }
        try {
            switch ($action) {
                "approve" { Post "$api/$($p.Id)/approve" @{ actor = "seed-script" } | Out-Null }
                "reject"  { Post "$api/$($p.Id)/reject"  @{ reason = "Seed: hồ sơ không hợp lệ" } | Out-Null }
                "retry"   { Post "$api/$($p.Id)/retry" $null | Out-Null }
                "abort"   { Post "$api/$($p.Id)/abort"   @{ reason = "Seed: huỷ onboarding" } | Out-Null }
            }
            Write-Host ("{0:HH:mm:ss} {1,-20} {2} [{3}] -> {4}" -f (Get-Date), $p.Name, $t.status, $t.currentStep, $action)
        } catch {
            # 409 khi trang thai vua doi giua luc doc va luc gui signal: bo qua, vong sau xu ly tiep
        }
    }
    if ($pending -eq 0) { break }
    Start-Sleep -Seconds 5
}

foreach ($p in $late) {
    $p.Id = New-Tenant $p
    Write-Host ("Created {0,-20} id={1} -> để nguyên, đang chạy" -f $p.Name, $p.Id)
    $plans += $p
}

Write-Host ""
Write-Host "===== Kết quả ====="
foreach ($p in $plans) {
    $t = Invoke-RestMethod "$api/$($p.Id)"
    Write-Host ("{0} {1,-20} {2,-16} {3}" -f $p.Id, $p.Name, $t.status, $t.currentStep)
}

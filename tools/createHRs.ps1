[CmdletBinding(SupportsShouldProcess=$true)]
param (
    [parameter(Mandatory=$false, HelpMessage="The controller URL on which to operate")]
    [string] $controllerURL = "http://yourcontroller:8090",
    [parameter(Mandatory=$false, HelpMessage="Controller username to authenticate with")]
    [string] $userName = "youruser@customer1",
    [parameter(Mandatory=$false, HelpMessage="Password for authentication to controller")]
    [string] $userPassword = "yourpassword",
    [parameter(Mandatory=$false, HelpMessage="Monitor application for the rule")]
    [string] $monitorapp = "Monitor",
    [parameter(Mandatory=$false, HelpMessage="Monitor tier for the rule")]
    [string] $monitortier = "Monitor",
    [parameter(Mandatory=$false, HelpMessage="Monitor node for the rule")]
    [string] $monitornode = "Node1",
    [parameter(Mandatory=$true, HelpMessage="Health rule to replicate")]
    [string] $healthrule,
    [parameter(Mandatory=$false, HelpMessage="List of applications for this dashboard")]
    [string] $list,
    [parameter(Mandatory=$false, HelpMessage="Replicate to all applications if true")]
    [boolean] $all
    )

#Requires -Version 2

$url = "$controllerURL/controller/rest/applications"
$req = New-Object System.Net.WebClient
$req.Headers["Authorization"] = "Basic " + [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$($userName):$($userPassword)"))
[xml] $appsxml = $req.DownloadString($url)
$allapps = @()
foreach ($app in $appsxml.applications.application) {
    $allapps += $app
}

$listapps = @()
if ($list -ne "") {
    $listapps = Get-Content $list
}

if ($all -eq 0 -and $listapps.Length -eq 0) {
    write-host No applications for replication
    exit 1
}

$appnames = @()
if ($all -eq 1) {
    foreach ($app in $allapps) {
        $name = $app.name -as [string]
        $appnames += $name
    }
}
else { 
    foreach ($name in $listapps) {
        $appnames += $name
    }
}

write-host Replicating $healthrule to $appnames

$healthruleurl = "$controllerURL/controller/healthrules/$monitorapp"
$healthrulereq = New-Object System.Net.WebClient
$healthrulereq.Headers["Authorization"] = "Basic " + [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$($userName):$($userPassword)"))

$basehealthrule = "Base$healthrule"
(Get-Content $healthrule) -replace '{server}', $controllerURL -replace '{monitorapp}', $monitorapp -replace '{monitortier}', $monitortier -replace '{monitornode}', $monitornode | Set-Content $basehealthrule

foreach($a in $appnames) {

    $newhealthrule = $a + $healthrule
    (Get-Content $basehealthrule) -replace '{app_name}', $a | Set-Content $newhealthrule

    $file = Get-ChildItem($basehealthrule)

    write-host Importing $newhealthrule
    try {
        $file = Get-ChildItem($newhealthrule)
		$responseArray = $healthrulereq.UploadFile($healthruleurl, "POST", $file)
        [System.Text.Encoding]::ASCII.GetString($responseArray)
        rm $file
    }
    catch {
	    write-Host $_.exception.message
		exit
	}		
}

rm $basehealthrule

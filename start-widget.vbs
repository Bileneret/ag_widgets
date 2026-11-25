' Starts the AG Widgets desktop app without a console window.
' Requires dependencies from install.ps1 (desktop\node_modules).

Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")

projectRoot = fso.GetParentFolderName(WScript.ScriptFullName)
appDir = projectRoot & "\desktop"
electronExe = appDir & "\node_modules\electron\dist\electron.exe"

If Not fso.FileExists(electronExe) Then
  MsgBox "Electron is not installed." & vbCrLf & vbCrLf & _
         "Run install.ps1 in " & projectRoot & " first.", _
         vbExclamation, "AG Widgets"
  WScript.Quit 1
End If

shell.CurrentDirectory = appDir
shell.Run """" & electronExe & """ .", 1, False

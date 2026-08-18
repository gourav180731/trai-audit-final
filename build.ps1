# Get short path to avoid issues with spaces
$shortPath = (New-Object -ComObject Scripting.FileSystemObject).GetFolder("C:\Program Files\Java\jdk-22").ShortPath
Write-Host "Building TRAI Audit Web Application..."
Write-Host "JAVA_HOME: $shortPath"
Write-Host "Starting Maven build (this will take 2-5 minutes on first run)..."
& "$shortPath\bin\java.exe" -classpath ".mvn\wrapper\maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=$PWD" org.apache.maven.wrapper.MavenWrapperMain clean package -DskipTests

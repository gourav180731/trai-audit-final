# How to Install Maven and Run the TRAI Audit Web Application

## Issue
The Maven wrapper (mvnw) is having trouble with spaces in the JAVA_HOME path on Windows.

## Solution: Install Maven Manually

### Option 1: Using Chocolatey (Recommended - Fastest)

If you have Chocolatey package manager installed:

```cmd
choco install maven
```

Then restart your terminal and run:
```cmd
cd c:\Users\91958\OneDrive\Desktop\trai-audit-final
mvn clean package -DskipTests
```

### Option 2: Manual Installation

1. **Download Maven**:
   - Go to: https://maven.apache.org/download.cgi
   - Download "apache-maven-3.9.5-bin.zip" (Binary zip archive)

2. **Extract Maven**:
   - Extract to: `C:\Apache\maven`
   - Should look like: `C:\Apache\maven\bin\mvn.cmd`

3. **Add to PATH**:
   - Open Windows Search → "Environment Variables"
   - Edit "System Variables" → "Path"
   - Add new entry: `C:\Apache\maven\bin`
   - Click OK to save

4. **Verify Installation**:
   Open new Command Prompt and run:
   ```cmd
   mvn --version
   ```
   Should show: Apache Maven 3.9.5

5. **Build the Project**:
   ```cmd
   cd c:\Users\91958\OneDrive\Desktop\trai-audit-final
   set JAVA_HOME=C:\Program Files\Java\jdk-22
   mvn clean package -DskipTests
   ```

6. **Run the Application**:
   ```cmd
   java -jar target\trai-audit-webapp-1.0.0.jar
   ```

7. **Access the Application**:
   Open browser: http://localhost:8080

## Alternative: Use IntelliJ IDEA or Eclipse

### Using IntelliJ IDEA (Easiest)

1. Open IntelliJ IDEA
2. File → Open → Select `c:\Users\91958\OneDrive\Desktop\trai-audit-final`
3. Wait for Maven import to complete (bottom right corner shows progress)
4. Find `TraiAuditWebApplication.java` in the Project panel
5. Right-click → Run 'TraiAuditWebApplication.main()'
6. Application starts, open browser: http://localhost:8080

### Using Eclipse

1. Open Eclipse
2. File → Import → Maven → Existing Maven Projects
3. Browse to: `c:\Users\91958\OneDrive\Desktop\trai-audit-final`
4. Finish
5. Right-click project → Run As → Spring Boot App
6. Application starts, open browser: http://localhost:8080

## What to Do Next

Once the application is running:

1. **Open Dashboard**: http://localhost:8080
   - You'll see "No data available" initially

2. **Upload Files**: Click "Upload Files" button
   - Select `WarningDetailedReport_2026-08-11_9_56.xlsx`
   - Select `TRAI_Wireless_Subscriber_Base.xlsx`
   - Click "Process Files"

3. **View Results**: Dashboard updates with:
   - 787 alerts processed
   - 3,935 TSP rows
   - Discrepancy counts per category

4. **Check Console**: Look for regression validation:
   ```
   Category 5: 147 flagged (expected: 147) ✓
   Category 6: 571 flagged (expected: 571) ✓
   Category 7: 479 flagged (expected: 479) ✓
   ```

## Troubleshooting

### "mvn: command not found"
- Restart terminal after installation
- Verify PATH includes Maven bin directory
- Try opening new Command Prompt window

### "JAVA_HOME not set"
Before running Maven:
```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-22
```

### "Port 8080 already in use"
Edit `src\main\resources\application.properties`:
```properties
server.port=8081
```

### Build fails with "Cannot resolve dependencies"
Check internet connection - Maven needs to download dependencies on first build (2-5 minutes).

## Quick Reference Commands

```cmd
# Build
mvn clean package -DskipTests

# Run
java -jar target\trai-audit-webapp-1.0.0.jar

# Run with custom config
java -jar target\trai-audit-webapp-1.0.0.jar --server.port=8081

# Build and run in one step (during development)
mvn spring-boot:run
```

## Expected Build Output

Successful build shows:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  45.123 s
[INFO] Finished at: 2026-08-17T10:30:00+05:30
[INFO] ------------------------------------------------------------------------
```

JAR file created at: `target\trai-audit-webapp-1.0.0.jar`

## Need Help?

See the comprehensive documentation:
- **WEB_APP_README.md** - Full guide
- **QUICK_START.md** - Quick reference
- **IMPLEMENTATION_SUMMARY.md** - What's complete vs remaining
- **PROJECT_DELIVERY_SUMMARY.md** - Overall project status

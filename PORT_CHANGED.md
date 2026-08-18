# Port Change - v1.2.3

**Date**: 18 August 2026, 4:23 PM IST  
**Change**: Application port changed from 8080 to 8081

## New Application URL

### Access the application at:
```
http://localhost:8081
```

## All Endpoints (Updated)

- **Dashboard**: http://localhost:8081
- **Upload**: http://localhost:8081/upload
- **Search**: http://localhost:8081/search
- **Category Detail**: http://localhost:8081/category/{id}
- **Alert Detail**: http://localhost:8081/alert/{id}
- **Discrepancy Detail**: http://localhost:8081/discrepancy/{id}
- **TSP Report Download**: http://localhost:8081/tsp-report/download/{tsp}
- **TSP Report Download All**: http://localhost:8081/tsp-report/download-all
- **H2 Console** (debugging): http://localhost:8081/h2-console

## Configuration Change

File: `src/main/resources/application.properties`

```properties
# Changed from:
server.port=8080

# Changed to:
server.port=8081
```

## Why the Change?

User requested port change from 8080 to 8081. Common reasons:
- Port 8080 may be used by another application
- Organizational policy or preference
- Avoiding conflicts with other services

## Application Status

- **Version**: 1.2.3 - Port Changed to 8081
- **Running on**: http://localhost:8081
- **Process ID**: 40036
- **Terminal ID**: 5
- **Started**: 18 August 2026, 4:23:14 PM IST
- **Database**: `./data/trai_audit_db.mv.db` (persistent, data intact)

## GitHub Repository

- **Repository**: https://github.com/gourav180731/trai-audit-final.git
- **Commit**: `d6e1e49` - "config: Change application port from 8080 to 8081"
- **Branch**: main
- **Total commits**: 5

## Features (All Working on Port 8081)

✅ Dashboard with live discrepancy tracking  
✅ File upload (Excel processing)  
✅ Search & drill-down navigation  
✅ TSP-wise Excel report downloads with exact styling  
✅ Persistent H2 database  
✅ All 9 discrepancy categories detection  

## Testing

1. Open your browser
2. Navigate to: **http://localhost:8081**
3. You should see the dashboard
4. All features work exactly as before, just on the new port

## Notes for Documentation

All documentation files (*.md) in this repository still reference port **8080**. They remain as historical reference. The **actual running application is on port 8081** as configured in `application.properties`.

If you need to update all documentation files to reflect the new port, a find-and-replace of `8080` to `8081` can be performed across all `.md` files.

---

**Application running successfully on port 8081!** ✅

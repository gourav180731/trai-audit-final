package com.audit.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main entry point for TRAI SMS Dissemination Audit Web Application.
 * 
 * Full-stack application built per official problem statement requirements:
 * - 9 discrepancy categories across TSP SMS dissemination compliance
 * - Reuses 100% of validated CLI detection engine logic
 * - Persists all discrepancies as structured DB records (H2 embedded)
 * - Web dashboard with drill-down navigation and filtering
 * - Report generation (Excel, CSV)
 * 
 * See WEB_APP_README.md for full documentation, including NEEDS_SIGN_OFF items
 * requiring confirmation from DoT/NDMA officials.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.audit.webapp", "com.audit"})
@EnableJpaRepositories(basePackages = "com.audit.webapp.repository")
@EntityScan(basePackages = "com.audit.webapp.entity")
public class TraiAuditWebApplication {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("TRAI SMS Dissemination Audit - Web Application");
        System.out.println("Discrepancy Detection Engine with Persistent Monitoring Dashboard");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Starting application...");
        System.out.println("Dashboard will be available at: http://localhost:8080");
        System.out.println("H2 Console (debugging): http://localhost:8080/h2-console");
        System.out.println();
        System.out.println("NOTE: See WEB_APP_README.md for 6 items requiring sign-off from DoT/NDMA.");
        System.out.println("=".repeat(80));
        System.out.println();

        SpringApplication.run(TraiAuditWebApplication.class, args);
    }
}

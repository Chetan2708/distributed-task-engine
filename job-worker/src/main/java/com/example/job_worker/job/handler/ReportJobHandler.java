package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import com.example.job_worker.job.JobRepository;
import com.example.job_worker.job.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReportJobHandler implements JobHandler {

    private final JobRepository jobRepository;

    @Override
    public String type() {
        return "REPORT";
    }

    @Override
    public JobResult execute(JobEntity job) {
        log.info("Generating system analytics report for job {}", job.getId());

        try {
            Map<String, Object> report = new HashMap<>();
            long totalJobs = jobRepository.count();
            report.put("totalJobs", totalJobs);

            // Jobs by status breakdown
            Map<String, Long> byStatus = new LinkedHashMap<>();
            List<Object[]> statusRows = jobRepository.countJobsByStatus();
            if (statusRows != null) {
                for (Object[] row : statusRows) {
                    if (row != null && row.length >= 2 && row[0] != null) {
                        String statusName = row[0] instanceof JobStatus 
                                ? ((JobStatus) row[0]).name() 
                                : String.valueOf(row[0]);
                        Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
                        byStatus.put(statusName, count);
                    }
                }
            }
            report.put("jobsByStatus", byStatus);

            // Jobs by type breakdown
            Map<String, Long> byType = new LinkedHashMap<>();
            List<Object[]> typeRows = jobRepository.countJobsByType();
            if (typeRows != null) {
                for (Object[] row : typeRows) {
                    if (row != null && row.length >= 2 && row[0] != null) {
                        String typeName = String.valueOf(row[0]);
                        Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
                        byType.put(typeName, count);
                    }
                }
            }
            report.put("jobsByType", byType);

            // Average processing time
            Double avgSeconds = jobRepository.avgProcessingTimeSeconds();
            double roundedAvg = avgSeconds != null ? Math.round(avgSeconds * 100.0) / 100.0 : 0.0;
            report.put("avgProcessingTimeSec", roundedAvg);

            // Success rate
            long successCount = byStatus.getOrDefault("SUCCESS", 0L);
            String successRate = totalJobs > 0 
                    ? (Math.round((successCount * 100.0) / totalJobs * 10.0) / 10.0) + "%" 
                    : "0.0%";
            report.put("successRate", successRate);

            report.put("generatedAt", LocalDateTime.now().toString());

            return new JobResult(true, "Report generated successfully with real system analytics", report);
        } catch (Exception e) {
            log.error("Error generating report for job {}: {}", job.getId(), e.getMessage(), e);
            return new JobResult(false, "Failed to generate report: " + e.getMessage());
        }
    }
}

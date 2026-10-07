package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PdfJobHandler implements JobHandler {
    @Override
    public String type() {
        return "PDF_GENERATION";
    }

    @Override
    public JobResult execute(JobEntity job) {
        log.info("Generating PDF for job {}", job.getId());
        try {
            // Simulate heavy lifting (e.g. headless browser rendering)
            Thread.sleep(3000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JobResult(false, "PDF generation interrupted");
        }
        return new JobResult(true, "PDF generated successfully");
    }
}

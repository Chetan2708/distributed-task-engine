package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import org.springframework.stereotype.Component;

/**
 * Convenience alias allowing clients to specify type="WEBHOOK" as well as "HTTP_WEBHOOK".
 */
@Component
public class WebhookAliasHandler implements JobHandler {

    private final HttpWebhookJobHandler delegate;

    public WebhookAliasHandler(HttpWebhookJobHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public String type() {
        return "WEBHOOK";
    }

    @Override
    public JobResult execute(JobEntity job) {
        return delegate.execute(job);
    }
}

package com.example.job_worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobWorkerApplication.class, args);
	}

}

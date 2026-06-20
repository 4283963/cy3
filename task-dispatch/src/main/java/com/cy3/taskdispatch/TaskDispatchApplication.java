package com.cy3.taskdispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.cy3.common.entity")
@EnableJpaRepositories(basePackages = "com.cy3.taskdispatch.repository")
public class TaskDispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskDispatchApplication.class, args);
    }
}

package com.jms.seafoodai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SeafoodAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeafoodAiApplication.class, args);
    }
}

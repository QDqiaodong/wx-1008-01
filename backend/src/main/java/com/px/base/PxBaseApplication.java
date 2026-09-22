package com.px.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PxBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(PxBaseApplication.class, args);
    }
}

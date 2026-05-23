package com.monit.pingbell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PingbellApplication {

    public static void main(String[] args) {
        SpringApplication.run(PingbellApplication.class, args);
    }

}

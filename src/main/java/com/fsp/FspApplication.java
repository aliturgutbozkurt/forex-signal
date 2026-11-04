package com.fsp;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@NullMarked
@Modulithic(
        systemName = "Forex Signal Platform",
        sharedModules = {"shared"}
)
@SpringBootApplication
@EnableScheduling
public class FspApplication {

    public static void main(String[] args) {
        SpringApplication.run(FspApplication.class, args);
    }
}


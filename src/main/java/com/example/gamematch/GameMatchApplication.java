package com.example.gamematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GameMatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameMatchApplication.class, args);
    }
}

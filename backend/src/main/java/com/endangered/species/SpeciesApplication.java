package com.endangered.species;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the endangered-species REST API. */
@SpringBootApplication
public class SpeciesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeciesApplication.class, args);
    }
}

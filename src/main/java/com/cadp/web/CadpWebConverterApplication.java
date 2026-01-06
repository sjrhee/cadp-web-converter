package com.cadp.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CadpWebConverterApplication {

    public static void main(String[] args) {
        // Debug: Check current working directory and .env file existence
        String cwd = System.getProperty("user.dir");
        System.out.println("[DEBUG] Current Working Directory: " + cwd);
        java.io.File envFile = new java.io.File(cwd, ".env");
        System.out.println("[DEBUG] .env file exists: " + envFile.exists() + " at " + envFile.getAbsolutePath());

        // Load .env file from current directory
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
            .directory(cwd)
            .load();
        
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
            if (entry.getKey().startsWith("CADP_")) {
                System.out.println("[DEBUG] Loaded from .env: " + entry.getKey() + "=" + entry.getValue());
            }
        });

        SpringApplication.run(CadpWebConverterApplication.class, args);
    }

}

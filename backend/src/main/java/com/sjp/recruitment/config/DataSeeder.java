package com.sjp.recruitment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.data-seeder.enabled", havingValue = "true")
public class DataSeeder {
    // Seed data for the remote PostgreSQL schema is managed with SQL scripts.
}

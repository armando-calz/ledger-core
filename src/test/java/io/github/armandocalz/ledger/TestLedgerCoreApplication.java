package io.github.armandocalz.ledger;

import org.springframework.boot.SpringApplication;

/** Runs the service against a throwaway PostgreSQL container: {@code ./mvnw spring-boot:test-run}. */
public class TestLedgerCoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(LedgerCoreApplication::main).with(PostgresTestcontainer.class).run(args);
    }
}

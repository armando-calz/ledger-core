package io.github.armandocalz.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class LedgerCoreApplicationIT {

    @Test
    void contextLoads() {
    }
}

package io.github.armandocalz.ledger.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.armandocalz.ledger.PostgresTestcontainer;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class LedgerApiIT {

    @Autowired MockMvcTester mvc;

    private String openAccount(String name, String type, String currency) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "type": "%s", "currency": "%s"}
                        """.formatted(name, type, currency))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcTestResult transfer(String debit, String credit, String amount, String currency) {
        return mvc.post().uri("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"debitAccountId": "%s", "creditAccountId": "%s",
                         "amount": "%s", "currency": "%s", "description": "test"}
                        """.formatted(debit, credit, amount, currency))
                .exchange();
    }

    @Test
    void depositThenPayBetweenWallets() throws Exception {
        String cash = openAccount("Cash at bank", "ASSET", "MXN");
        String alice = openAccount("Alice wallet", "LIABILITY", "MXN");
        String bob = openAccount("Bob wallet", "LIABILITY", "MXN");

        assertThat(transfer(cash, alice, "500.00", "MXN")).hasStatus(HttpStatus.CREATED);
        assertThat(transfer(alice, bob, "100.00", "MXN")).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.postings[*].amount").asArray().containsExactly("100.00", "-100.00");

        assertThat(mvc.get().uri("/api/v1/accounts/{id}/balance", alice))
                .hasStatusOk().bodyJson().extractingPath("$.amount").isEqualTo("400.00");
        assertThat(mvc.get().uri("/api/v1/accounts/{id}/balance", bob))
                .hasStatusOk().bodyJson().extractingPath("$.amount").isEqualTo("100.00");
        assertThat(mvc.get().uri("/api/v1/accounts/{id}/balance", cash))
                .hasStatusOk().bodyJson().extractingPath("$.amount").isEqualTo("500.00");

        assertThat(mvc.get().uri("/api/v1/accounts/{id}/movements", alice))
                .hasStatusOk().bodyJson().extractingPath("$[*].amount").asArray()
                .as("newest first, from Alice's point of view")
                .containsExactly("-100.00", "500.00");
    }

    @Test
    void getsAnAccount() throws Exception {
        String id = openAccount("Alice wallet", "LIABILITY", "MXN");

        assertThat(mvc.get().uri("/api/v1/accounts/{id}", id)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"id": "%s", "name": "Alice wallet", "type": "LIABILITY", "currency": "MXN", "status": "ACTIVE",
                         "allowNegativeBalance": false}
                        """.formatted(id));
    }

    @Nested
    class Errors {

        @Test
        void unknownAccountIs404() throws Exception {
            assertThat(mvc.get().uri("/api/v1/accounts/{id}", UUID.randomUUID()))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.title").isEqualTo("Account not found");
        }

        @Test
        void malformedRequestsAre400() throws Exception {
            String alice = openAccount("Alice wallet", "LIABILITY", "MXN");
            String bob = openAccount("Bob wallet", "LIABILITY", "MXN");

            assertThat(transfer(alice, bob, "12.3e4", "MXN")).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(transfer(alice, bob, "-5.00", "MXN")).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(transfer(alice, bob, "5.00", "mxn")).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void ruleViolationsAre422() throws Exception {
            String alice = openAccount("Alice wallet", "LIABILITY", "MXN");
            String bob = openAccount("Bob wallet", "LIABILITY", "MXN");

            assertThat(transfer(alice, bob, "5.001", "MXN")).as("too many decimals")
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(transfer(alice, bob, "5.00", "USD")).as("wrong currency for the accounts")
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(transfer(alice, alice, "5.00", "MXN")).as("same account")
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(transfer(alice, bob, "0", "MXN")).as("zero amount")
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        }

        @Test
        void overdraftIs422WithTheAvailableBalance() throws Exception {
            String cash = openAccount("Cash at bank", "ASSET", "MXN");
            String alice = openAccount("Alice wallet", "LIABILITY", "MXN");
            String bob = openAccount("Bob wallet", "LIABILITY", "MXN");
            transfer(cash, alice, "50.00", "MXN");

            assertThat(transfer(alice, bob, "50.01", "MXN"))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson()
                    .satisfies(json -> assertThat(json).extractingPath("$.title").isEqualTo("Insufficient funds"))
                    .extractingPath("$.detail").asString().contains("available 50.00 MXN", "requested 50.01 MXN");
            assertThat(mvc.get().uri("/api/v1/accounts/{id}/balance", alice))
                    .hasStatusOk().bodyJson().extractingPath("$.amount").isEqualTo("50.00");
        }

        @Test
        void failedTransfersDoNotMoveMoney() throws Exception {
            String alice = openAccount("Alice wallet", "LIABILITY", "MXN");
            String bob = openAccount("Bob wallet", "LIABILITY", "MXN");

            transfer(alice, bob, "5.00", "USD");

            assertThat(mvc.get().uri("/api/v1/accounts/{id}/movements", alice))
                    .hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
        }
    }

    @Test
    void publishesOpenApiDocs() throws Exception {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk()
                .bodyJson().extractingPath("$.info.title").isEqualTo("ledger-core API");
    }
}

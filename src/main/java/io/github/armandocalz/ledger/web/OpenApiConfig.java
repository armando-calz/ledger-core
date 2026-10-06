package io.github.armandocalz.ledger.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ledger-core API")
                .version("v1")
                .description("Double-entry ledger. Amounts are decimal strings in the currency's units "
                        + "(e.g. \"100.50\"), never floating-point numbers.")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

package com.ahmedali.claimguard.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AdjudiCore API",
                version = "v1",
                description = "Healthcare claims validation and adjudication API. "
                        + "Simulates the intake, validation, and adjudication "
                        + "workflow of a commercial insurance payer using "
                        + "synthetic patient data (Synthea-inspired).",
                contact = @Contact(name = "Ahmed Ali", email = "ahmedkali841@gmail.com"),
                license = @License(name = "MIT")
        ),
        servers = @Server(url = "/", description = "Current host")
)
public class OpenApiConfig {
}

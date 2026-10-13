package com.transporterassignment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Transporter Assignment on Lanes API",
        version = "1.0.0",
        description = OpenApiExamples.API_DESCRIPTION))
public class OpenApiConfig {
}

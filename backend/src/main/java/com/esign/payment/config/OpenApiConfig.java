package com.esign.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI esignPayOpenAPI() {
        // Server-level extension documenting gzip support
        Server server = new Server()
                .url("/")
                .description("ESignPay API Server — gzip compression enabled on all endpoints");
        Map<String, Object> serverExtensions = new LinkedHashMap<>();
        serverExtensions.put("x-server-compression", Map.of(
                "enabled", true,
                "algorithms", List.of("gzip"),
                "min-response-size", "256 bytes",
                "mime-types", List.of(
                        "application/json", "application/xml",
                        "text/html", "text/plain",
                        "application/hal+json", "application/cbor"
                )
        ));
        // Server-level extension documenting Range/206 partial content support
        serverExtensions.put("x-server-range-support", Map.of(
                "enabled", true,
                "unit", "bytes",
                "endpoints", List.of("/api/documents/{id}/download")
        ));
        server.setExtensions(serverExtensions);

        return new OpenAPI()
                .servers(List.of(server))
                .info(new Info()
                        .title("ESignPay API")
                        .version("1.0")
                        .description(
                                "API for managing document e-signatures, payments (Stripe 3D Secure), "
                                + "account opening with KYC/OCR verification, and OTP signing via Twilio SMS.\n\n"
                                + "### Compression\n"
                                + "All endpoints support **gzip compression**. Send `Accept-Encoding: gzip` "
                                + "to receive compressed responses (typically 60–80% payload reduction). "
                                + "Responses ≥ 256 bytes are automatically compressed.\n\n"
                                + "### Range / Partial Content (206)\n"
                                + "File download endpoints support **HTTP Range requests** (`Range: bytes=start-end`). "
                                + "The server responds with `206 Partial Content` and `Accept-Ranges: bytes`, "
                                + "enabling resumable downloads and partial fetches for large files.")
                        .contact(new Contact()
                                .name("ESignPay Team")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak JWT Bearer token — obtain from POST to "
                                        + "http://localhost:9090/realms/esignpayment/protocol/openid-connect/token"))
                        .addHeaders("Content-Encoding", new Header()
                                .description("Compression algorithm used (gzip when Accept-Encoding: gzip is sent)")
                                .schema(new StringSchema()._enum(List.of("gzip", "identity"))))
                        .addHeaders("Vary", new Header()
                                .description("Indicates response varies based on Accept-Encoding")
                                .schema(new StringSchema().example("Accept-Encoding")))
                        .addHeaders("Accept-Ranges", new Header()
                                .description("Indicates that the server supports Range requests")
                                .schema(new StringSchema()._enum(List.of("bytes", "none"))))
                        .addHeaders("Content-Range", new Header()
                                .description("Indicates the part of a document the server is returning (e.g. bytes 0-99/1234)")
                                .schema(new StringSchema().example("bytes 0-99/1234"))));
    }

    /**
     * Adds Content-Encoding and Vary response headers to every operation in the spec,
     * making gzip compression visible in Swagger UI for ALL endpoints.
     * Also adds Accept-Ranges header on download endpoints.
     */
    @Bean
    public OpenApiCustomizer gzipCompressionCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getResponses() != null) {
                        operation.getResponses().forEach((statusCode, apiResponse) -> {
                            apiResponse.addHeaderObject("Content-Encoding", new Header()
                                    .description("gzip when client sends Accept-Encoding: gzip")
                                    .schema(new StringSchema()._enum(List.of("gzip", "identity"))));
                            apiResponse.addHeaderObject("Vary", new Header()
                                    .description("Accept-Encoding")
                                    .schema(new StringSchema().example("Accept-Encoding")));
                        });
                    }
                }));
    }

    /**
     * Adds Range/206 Partial Content support to download endpoints in the spec.
     * Injects Accept-Ranges header and 206/416 response codes on paths containing "/download".
     */
    @Bean
    public OpenApiCustomizer rangePartialContentCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.toLowerCase().contains("/download")) {
                return;
            }
            pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                // Add Accept-Ranges header to the 200 response
                operation.getResponses().forEach((statusCode, resp) -> {
                    resp.addHeaderObject("Accept-Ranges", new Header()
                            .description("Indicates the server supports byte-range requests")
                            .schema(new StringSchema()._enum(List.of("bytes"))));
                });

                // Add 206 Partial Content response
                ApiResponse partialResponse = new ApiResponse()
                        .description("Partial Content — returned when a valid Range header is sent")
                        .addHeaderObject("Content-Range", new Header()
                                .description("Byte range of the returned content (e.g. bytes 0-99/1234)")
                                .schema(new StringSchema().example("bytes 0-99/1234")))
                        .addHeaderObject("Accept-Ranges", new Header()
                                .description("bytes")
                                .schema(new StringSchema()._enum(List.of("bytes"))))
                        .content(new Content().addMediaType("application/octet-stream",
                                new MediaType().schema(new Schema<byte[]>()
                                        .type("string").format("binary"))));
                operation.getResponses().addApiResponse("206", partialResponse);

                // Add 416 Range Not Satisfiable response
                ApiResponse rangeNotSatisfiable = new ApiResponse()
                        .description("Range Not Satisfiable — the requested range is invalid or beyond file size")
                        .addHeaderObject("Content-Range", new Header()
                                .description("Total file size (e.g. bytes */1234)")
                                .schema(new StringSchema().example("bytes */1234")));
                operation.getResponses().addApiResponse("416", rangeNotSatisfiable);

                // Add Range parameter if not already present
                if (operation.getParameters() == null
                        || operation.getParameters().stream()
                            .noneMatch(p -> "Range".equalsIgnoreCase(p.getName()))) {
                    operation.addParametersItem(
                            new io.swagger.v3.oas.models.parameters.HeaderParameter()
                                    .name("Range")
                                    .description("Byte range to fetch (e.g. bytes=0-99). "
                                            + "If omitted, the full file is returned.")
                                    .required(false)
                                    .schema(new StringSchema()
                                            .example("bytes=0-1023")));
                }
            });
        });
    }
}

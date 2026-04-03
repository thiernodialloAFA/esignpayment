package com.esign.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateDocumentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "File name is required")
    private String fileName;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotBlank(message = "File content is required (base64)")
    private String fileContent;

    private List<SignerRequest> signers = new ArrayList<>();

    @Data
    public static class SignerRequest {
        @NotBlank(message = "Signer email is required")
        private String email;

        @NotBlank(message = "Signer name is required")
        private String name;

        private String phone;
    }
}

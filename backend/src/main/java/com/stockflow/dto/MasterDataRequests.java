package com.stockflow.dto;

import jakarta.validation.constraints.*;

/** Input DTOs deliberately exclude database IDs, timestamps and stock quantities. */
public final class MasterDataRequests {
    private MasterDataRequests() {}

    public record CategoryInput(@NotBlank @Size(max = 100) String name) {
        public CategoryInput { name = trim(name); }
    }

    public record ProductInput(
            @NotNull @Positive Long categoryId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description) {
        public ProductInput { name = trim(name); description = optional(description); }
    }

    public record SkuInput(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String code,
            @NotBlank @Size(max = 200) String name) {
        public SkuInput { code = trim(code); name = trim(name); }
    }

    public record WarehouseInput(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String address) {
        public WarehouseInput { code = trim(code); name = trim(name); address = optional(address); }
    }

    public record EnabledInput(@NotNull Boolean enabled) {}

    private static String trim(String value) { return value == null ? null : value.strip(); }
    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

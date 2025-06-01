package com.example.dhtcopy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class KeyValueDto {
    @NotBlank(message = "Key cannot be blank")
    @Size(max = 255, message = "Key cannot exceed 255 characters")
    private String key;

    @NotBlank(message = "Value cannot be blank")
    @Size(max = 1000, message = "Value cannot exceed 1000 characters")
    private String value;

    // Constructors
    public KeyValueDto() {}

    public KeyValueDto(String key, String value) {
        this.key = key;
        this.value = value;
    }

    // Getters and setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}


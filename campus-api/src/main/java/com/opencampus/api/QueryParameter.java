package com.opencampus.api;

public record QueryParameter(
        String name,
        String description,
        boolean required
) {
}
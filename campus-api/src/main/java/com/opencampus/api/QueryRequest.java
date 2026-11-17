package com.opencampus.api;

import java.util.Map;

public record QueryRequest(
        String serviceId,
        Map<String, String> parameters
) {
}
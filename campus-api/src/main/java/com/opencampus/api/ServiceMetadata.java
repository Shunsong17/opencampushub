package com.opencampus.api;

import java.util.List;

public record ServiceMetadata(
        String serviceId,
        String name,
        String description,
        List<QueryParameter> parameters,
        String apiVersion
) {
}

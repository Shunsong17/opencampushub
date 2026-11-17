package com.opencampus.api;

import java.util.List;

public record QueryResult(
        boolean success,
        String serviceId,
        String message,
        List<ResultItem> items,
        ErrorInfo error
) {
}
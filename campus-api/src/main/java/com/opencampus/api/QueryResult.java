package com.opencampus.api;

import java.util.List;

public record QueryResult(
        boolean success,
        String serviceId,
        String message,
        List<ResultItem> items,
        ErrorInfo error
) {
    public static QueryResult success(
            String serviceId,
            String message,
            List<ResultItem> items
    ) {
        return new QueryResult(
                true,
                serviceId,
                message,
                items,
                null
        );
    }

    public static QueryResult failure(
            String serviceId,
            ErrorCode code,
            String message
    ) {
        return new QueryResult(
                false,
                serviceId,
                message,
                List.of(),
                new ErrorInfo(code, message)
        );
    }
}
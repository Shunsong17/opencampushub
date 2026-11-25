package com.opencampus.api;

public record ErrorInfo(
        ErrorCode code,
        String message
) {
}

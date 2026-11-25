package com.opencampus.api;

import java.util.Map;

public record ResultItem(
        String title,
        Map<String, String> fields
) {
}

package com.opencampus.core.announcement;

import java.time.LocalDate;

/** 一条从本地样例数据读取的校园公告。 */
public record Announcement(
        String id,
        String title,
        String category,
        LocalDate date,
        String content
) {
}

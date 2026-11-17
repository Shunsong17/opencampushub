package com.opencampus.plugin.course;

import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CourseServiceTest {
    private final CourseService service = new CourseService();

    private QueryResult query(Map<String, String> parameters) {
        return service.query(new QueryRequest(CourseService.SERVICE_ID, parameters));
    }

    @Test
    void nameAndOutputFields() {
        QueryResult result = query(Map.of("name", " 软件 "));
        assertTrue(result.success());
        assertNull(result.error());
        assertEquals(1, result.items().size());
        assertEquals("软件工程", result.items().get(0).title());
        assertEquals(Map.of("id", "C001", "teacher", "张老师", "time", "周三 08:00-09:35",
                "location", "A201"), result.items().get(0).fields());
        assertEquals("Java程序设计", query(Map.of("name", " JAVA ")).items().get(0).title());
    }

    @Test
    void dayAndIntersection() {
        assertEquals(2, query(Map.of("day", " 周三 ")).items().size());
        assertEquals("Java程序设计", query(Map.of("name", "java", "day", "周三",
                "time", "10:30")).items().get(0).title());
        assertTrue(query(Map.of("name", "软件", "day", "周四")).items().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:00", "08:30", "09:34"})
    void timeIncludesStartAndInterior(String time) {
        assertEquals("软件工程", query(Map.of("time", time)).items().get(0).title());
    }

    @ParameterizedTest
    @ValueSource(strings = {"07:59", "09:35", "23:59"})
    void timeExcludesEndAndOutside(String time) {
        QueryResult result = query(Map.of("time", time));
        assertTrue(result.success());
        assertTrue(result.items().isEmpty());
        assertNull(result.error());
        assertEquals("未找到匹配课程", result.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"8:00", "24:00", "12:60", "10:00-11:00", "abc"})
    void invalidTime(String time) {
        assertInvalid(query(Map.of("time", time)));
    }

    @Test
    void invalidRequests() {
        assertInvalid(query(Map.of()));
        assertInvalid(query(Map.of("name", "  ", "day", "")));
        assertInvalid(query(Map.of("day", "星期三")));
        assertInvalid(query(Map.of("unknown", "软件")));
        assertInvalid(query(null));
        assertInvalid(service.query(null));
        assertInvalid(service.query(new QueryRequest("other", Map.of("name", "软件"))));
    }

    @Test
    void missingData() {
        assertDataFailure(new CourseService(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "C001,软件工程,老师,周八,08:00-09:35,A201",
            "C001,软件工程,老师,周三,24:00-25:00,A201",
            "C001,软件工程,老师,周三,09:35-08:00,A201",
            "C001,软件工程,老师,周三,08:00-08:00,A201",
            "C001,软件工程,,周三,08:00-09:35,A201",
            "C001,软件工程,老师,周三,08:00-09:35",
            "C001,\"软件工程\",老师,周三,08:00-09:35,A201",
            "C001,软件工程,老师,周三,08:00-09:35,A201\nC001,软件工程,老师,周三,08:00-09:35,A201"
    })
    void malformedRows(String rows) {
        assertDataFailure(fromCsv("id,name,teacher,day,time,location\n" + rows));
    }

    @Test
    void headerAndEmptyDataset() {
        assertDataFailure(fromCsv("wrong\n"));
        QueryResult empty = fromCsv("id,name,teacher,day,time,location\n")
                .query(new QueryRequest("course_query", Map.of("name", "软件")));
        assertTrue(empty.success());
        assertTrue(empty.items().isEmpty());
        assertTrue(fromCsv("\uFEFFid,name,teacher,day,time,location\n\n"
                + "C001,软件工程,老师,周三,08:00-09:35,A201\n")
                .query(new QueryRequest("course_query", Map.of("name", "软件"))).success());
    }

    private static CourseService fromCsv(String csv) {
        return new CourseService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertInvalid(QueryResult result) {
        assertFalse(result.success());
        assertEquals(ErrorCode.INVALID_PARAMETER, result.error().code());
        assertTrue(result.items().isEmpty());
    }

    private static void assertDataFailure(CourseService service) {
        QueryResult result = service.query(new QueryRequest("course_query", Map.of("name", "软件")));
        assertFalse(result.success());
        assertEquals(ErrorCode.DATA_LOAD_FAILED, result.error().code());
        assertTrue(result.items().isEmpty());
    }
}

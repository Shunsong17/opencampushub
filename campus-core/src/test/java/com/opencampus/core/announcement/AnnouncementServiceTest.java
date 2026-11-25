package com.opencampus.core.announcement;

import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementServiceTest {
    private static final String HEADER = "id,title,category,date,content\n";
    private final AnnouncementService service = new AnnouncementService();

    @Test
    void declaresOptionalFiltersAndSupportedApi() {
        ServiceMetadata metadata = service.getMetadata();
        assertEquals("announcement_query", metadata.serviceId());
        assertEquals("公告查询", metadata.name());
        assertEquals("1.0", metadata.apiVersion());
        assertEquals(List.of("keyword", "category"),
                metadata.parameters().stream().map(QueryParameter::name).toList());
        assertTrue(metadata.parameters().stream().noneMatch(QueryParameter::required));
    }

    @Test
    void keywordMatchesTitleAndReturnsCompleteFields() {
        QueryResult result = query(Map.of("keyword", " 图书馆 "));
        assertSuccess(result);
        assertEquals(1, result.items().size());
        assertEquals("图书馆闭馆通知", result.items().get(0).title());
        assertEquals(Map.of("id", "A002", "category", "服务通知", "date", "2026-11-22",
                "content", "图书馆设备维护期间暂停开放"), result.items().get(0).fields());
    }

    @Test
    void keywordAlsoMatchesContentAndIgnoresEnglishCase() {
        assertEquals(List.of("A002"), ids(query(Map.of("keyword", "设备维护"))));
        assertEquals(List.of("A004"), ids(query(Map.of("keyword", " java "))));
    }

    @Test
    void categoryIsExactAndKeepsCsvOrder() {
        assertEquals(List.of("A001", "A004"), ids(query(Map.of("category", " 活动 "))));
        assertEquals(List.of("A002", "A005"), ids(query(Map.of("category", "服务通知"))));
        assertNoMatch(query(Map.of("category", "通知")));
    }

    @Test
    void combinedFiltersUseIntersection() {
        QueryResult result = query(Map.of("keyword", "维护", "category", "服务通知"));
        assertSuccess(result);
        assertEquals(List.of("A002", "A005"), ids(result));
        assertNoMatch(query(Map.of("keyword", "图书馆", "category", "活动")));
    }

    @Test
    void emptyAndBlankFiltersListAllAnnouncements() {
        List<String> all = List.of("A001", "A002", "A003", "A004", "A005");
        assertEquals(all, ids(query(Map.of())));
        assertEquals(all, ids(query(Map.of("keyword", " \t ", "category", ""))));
    }

    @Test
    void nullValuesAreAbsentFilters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("keyword", null);
        parameters.put("category", "活动");
        assertEquals(List.of("A001", "A004"), ids(query(parameters)));
        parameters.put("category", null);
        assertEquals(5, query(parameters).items().size());
    }

    @Test
    void noMatchIsSuccessWithEmptyItems() {
        assertNoMatch(query(Map.of("keyword", "不存在的公告")));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequests(QueryRequest request) {
        QueryResult result = service.query(request);
        assertFailure(result, ErrorCode.INVALID_PARAMETER);
        assertEquals(request == null ? null : request.serviceId(), result.serviceId());
    }

    static Stream<QueryRequest> invalidRequests() {
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "活动");
        return Stream.of(null,
                new QueryRequest("other", Map.of()),
                new QueryRequest(" ", Map.of()),
                new QueryRequest(null, Map.of()),
                new QueryRequest(AnnouncementService.SERVICE_ID, null),
                new QueryRequest(AnnouncementService.SERVICE_ID, Map.of("unknown", "活动")),
                new QueryRequest(AnnouncementService.SERVICE_ID, nullKey));
    }

    @Test
    void missingDataReturnsDeclaredFailure() {
        QueryResult result = new AnnouncementService(null).query(request(Map.of()));
        assertFailure(result, ErrorCode.DATA_LOAD_FAILED);
        assertTrue(result.message().contains("announcements.csv"));
    }

    @Test
    void requestValidationPrecedesDataFailure() {
        AnnouncementService unavailable = new AnnouncementService(null);
        assertFailure(unavailable.query(request(Map.of("unknown", "活动"))),
                ErrorCode.INVALID_PARAMETER);
        assertFailure(unavailable.query(request(null)), ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void readFailureIsContainedAndInputIsClosed() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("模拟读取失败");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        QueryResult result = new AnnouncementService(broken).query(request(Map.of()));
        assertFailure(result, ErrorCode.DATA_LOAD_FAILED);
        assertTrue(result.message().contains("模拟读取失败"));
        assertTrue(closed.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A001,标题,活动,2026-11-20",
            "A001,标题,活动,2026-11-20,内容,额外列",
            "A001, ,活动,2026-11-20,内容",
            "A001,标题,活动,2026-11-20,",
            "A001,标题,活动,2026-02-30,内容",
            "A001,标题,活动,2026-1-20,内容",
            "A001,标题,活动,昨天,内容",
            "VALID,另一标题,活动,2026-11-21,内容",
            "A001,\"未闭合标题,活动,2026-11-20,内容",
            "A001,标\"题,活动,2026-11-20,内容",
            "A001,\"标题\"尾部,活动,2026-11-20,内容"
    })
    void malformedRowsFailWholeDatasetAndIdentifyLine(String row) {
        String valid = "VALID,正常公告,活动,2026-11-19,正常内容\n";
        QueryResult result = fromCsv(HEADER + valid + row).query(request(Map.of()));
        assertFailure(result, ErrorCode.DATA_LOAD_FAILED);
        assertTrue(result.message().contains("行错误"));
        assertTrue(result.message().contains("第 3 行"), result.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "wrong\n", "title,id,category,date,content\n"})
    void rejectsEmptyFilesAndWrongHeaders(String csv) {
        assertFailure(fromCsv(csv).query(request(Map.of())), ErrorCode.DATA_LOAD_FAILED);
    }

    @Test
    void headerOnlyIsAnEmptyDataset() {
        assertNoMatch(fromCsv(HEADER).query(request(Map.of())));
    }

    @Test
    void acceptsBomBlankLinesAndFieldWhitespace() {
        QueryResult result = fromCsv("\uFEFF" + HEADER
                + "\n  \n A001 , 标题 , 活动 , 2026-11-20 , 内容 \n")
                .query(request(Map.of("category", "活动")));
        assertSuccess(result);
        assertEquals(List.of("A001"), ids(result));
        assertEquals("标题", result.items().get(0).title());
    }

    @Test
    void readsQuotedCommasAndEscapedQuotes() {
        QueryResult result = fromCsv(HEADER
                + "A001,\"图书馆,通知\",服务通知,2026-11-20,\"维护时间为18:00,请查看\"\"通知\"\"\"\n")
                .query(request(Map.of("keyword", "18:00")));
        assertSuccess(result);
        assertEquals(1, result.items().size());
        assertEquals("图书馆,通知", result.items().get(0).title());
        assertEquals("维护时间为18:00,请查看\"通知\"", result.items().get(0).fields().get("content"));
        assertEquals("网络维护时间为18:00,请提前保存资料",
                query(Map.of("keyword", "校园网络维护")).items().get(0).fields().get("content"));
    }

    private QueryResult query(Map<String, String> parameters) {
        return service.query(request(parameters));
    }

    private static QueryRequest request(Map<String, String> parameters) {
        return new QueryRequest(AnnouncementService.SERVICE_ID, parameters);
    }

    private static AnnouncementService fromCsv(String csv) {
        return new AnnouncementService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> ids(QueryResult result) {
        assertSuccess(result);
        return result.items().stream().map(item -> item.fields().get("id")).toList();
    }

    private static void assertSuccess(QueryResult result) {
        assertTrue(result.success(), result.message());
        assertNull(result.error());
        assertEquals(AnnouncementService.SERVICE_ID, result.serviceId());
        assertNotNull(result.items());
        for (ResultItem item : result.items()) {
            assertFalse(item.title().isBlank());
            assertNotNull(item.fields());
        }
    }

    private static void assertNoMatch(QueryResult result) {
        assertSuccess(result);
        assertTrue(result.items().isEmpty());
        assertEquals("未找到匹配结果", result.message());
    }

    private static void assertFailure(QueryResult result, ErrorCode code) {
        assertFalse(result.success());
        assertNotNull(result.error());
        assertEquals(code, result.error().code());
        assertTrue(result.items().isEmpty());
        assertFalse(result.message().isBlank());
    }
}

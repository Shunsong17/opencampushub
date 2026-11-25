package com.opencampus.plugin.canteen;

import com.opencampus.api.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CanteenDataTest {
    @ParameterizedTest
    @ValueSource(strings = {"M001,菜,食堂,2026-02-30,午餐,8.50,窗口",
            "M001,菜,食堂,2026-11-25,夜宵,8.50,窗口",
            "M001,菜,食堂,2026-11-25,午餐,-1,窗口",
            "M001,菜,食堂,2026-11-25,午餐,1.234,窗口"})
    void rejectsInvalidDomainData(String row) {
        String csv = "id,name,canteen,date,meal,price,window\n" + row;
        var service = new CanteenService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        var result = service.query(new QueryRequest("canteen_query", Map.of("name", "x")));
        assertEquals(ErrorCode.DATA_LOAD_FAILED, result.error().code());
        assertTrue(result.error().message().contains("2"));
        assertTrue(result.items().isEmpty());
    }

    @Test
    void closesInputAndAcceptsBomAndBlankLines() {
        String csv = "\uFEFFid,name,canteen,date,meal,price,window\n\nM001,番茄炒蛋,第一食堂,2026-11-25,午餐,8.50,一号窗口\n";
        boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        var service = new CanteenService(input);
        assertTrue(closed[0]);
        assertEquals(1, service.query(new QueryRequest("canteen_query",
                Map.of("name", "番茄炒蛋"))).items().size());
    }

    @Test
    void mapsReadFailure() {
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("unreadable");
            }
        };
        var service = new CanteenService(input);
        assertEquals(ErrorCode.DATA_LOAD_FAILED, service.query(new QueryRequest("canteen_query",
                Map.of("name", "x"))).error().code());
    }
}

package com.opencampus.plugin.library;

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

class LibraryDataTest {
    @ParameterizedTest
    @ValueSource(strings = {"B001,书,作者,分类,位置,-1",
            "B001,书,作者,分类,位置,1.5"})
    void rejectsInvalidDomainData(String row) {
        String csv = "id,name,author,category,location,available\n" + row;
        var service = new LibraryService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        var result = service.query(new QueryRequest("library_query", Map.of("name", "x")));
        assertEquals(ErrorCode.DATA_LOAD_FAILED, result.error().code());
        assertTrue(result.error().message().contains("2"));
        assertTrue(result.items().isEmpty());
    }

    @Test
    void closesInputAndAcceptsBomAndBlankLines() {
        String csv = "\uFEFFid,name,author,category,location,available\n\nB001,Java程序设计,张老师,计算机,三楼A区,3\n";
        boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        var service = new LibraryService(input);
        assertTrue(closed[0]);
        assertEquals(1, service.query(new QueryRequest("library_query",
                Map.of("name", "Java程序设计"))).items().size());
    }

    @Test
    void mapsReadFailure() {
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("unreadable");
            }
        };
        var service = new LibraryService(input);
        assertEquals(ErrorCode.DATA_LOAD_FAILED, service.query(new QueryRequest("library_query",
                Map.of("name", "x"))).error().code());
    }
}

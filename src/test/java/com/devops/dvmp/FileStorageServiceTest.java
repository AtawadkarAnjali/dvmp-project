package com.devops.dvmp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir Path dir;

    @Test
    void previewReadsColumnsTypesAndRowCount() throws Exception {
        FileStorageService s = new FileStorageService(dir.toString());
        String csv = "id,price,name\n1,2.5,\"Smith, J\"\n2,3.0,Lee\n3,4.1,Kim\n";
        StoredFile f = s.storeBytes("data.csv", csv.getBytes(StandardCharsets.UTF_8));

        FilePreview p = s.preview(f.storedName(), f.originalName());
        assertNotNull(p);
        assertEquals(List.of("id", "price", "name"), p.columns());
        assertEquals(List.of("integer", "decimal", "text"), p.types());
        assertEquals(3, p.totalRows());
        assertEquals("Smith, J", p.rows().get(0).get(2));
        assertEquals(64, f.checksum().length());
    }

    @Test
    void nonCsvHasNoPreview() throws Exception {
        FileStorageService s = new FileStorageService(dir.toString());
        StoredFile f = s.storeBytes("data.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertNull(s.preview(f.storedName(), f.originalName()));
    }

    @Test
    void validateRejectsUnknownExtension() throws Exception {
        FileStorageService s = new FileStorageService(dir.toString());
        var bad = new org.springframework.mock.web.MockMultipartFile("file", "x.exe", "application/octet-stream", "x".getBytes());
        assertNotNull(s.validate(bad));
        assertNotNull(s.validate(null));
    }
}

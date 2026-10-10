package com.devops.dvmp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = new TreeSet<>(Set.of(
            "csv", "tsv", "json", "xml", "txt", "xlsx", "xls", "parquet", "zip", "gz", "png", "jpg", "jpeg"));

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** @return error message, or null when the file is acceptable */
    public String validate(MultipartFile f) {
        if (f == null || f.isEmpty()) return "Please attach a dataset file";
        String ext = extension(f.getOriginalFilename());
        if (!ALLOWED.contains(ext))
            return "File type ." + ext + " is not allowed. Allowed: " + String.join(", ", ALLOWED);
        return null;
    }

    public StoredFile store(MultipartFile f) {
        try (InputStream in = f.getInputStream()) {
            return save(f.getOriginalFilename(), in);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public StoredFile storeBytes(String name, byte[] data) {
        try (InputStream in = new ByteArrayInputStream(data)) {
            return save(name, in);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private StoredFile save(String originalName, InputStream in) throws IOException {
        String clean = cleanName(originalName);
        String ext = extension(clean);
        String stored = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = root.resolve(stored).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid path");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream din = new DigestInputStream(in, md)) {
                Files.copy(din, target, StandardCopyOption.REPLACE_EXISTING);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            return new StoredFile(clean, stored, Files.size(target), hex.toString(), ext.toUpperCase());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public Resource load(String storedName) {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) throw new IllegalArgumentException("File not found");
        return new FileSystemResource(p);
    }

    public void delete(String storedName) {
        if (storedName == null) return;
        try { Files.deleteIfExists(root.resolve(storedName).normalize()); } catch (IOException ignored) { }
    }

    private static String cleanName(String n) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(n == null ? "file" : n));
        if (name == null || name.isBlank()) name = "file";
        return name.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    private static String extension(String n) {
        if (n == null) return "";
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase();
    }

    // ---------- CSV / TSV preview ----------
    public FilePreview preview(String storedName, String originalName) {
        String ext = extension(originalName);
        if (storedName == null || !(ext.equals("csv") || ext.equals("tsv"))) return null;
        char delim = ext.equals("tsv") ? '\t' : ',';
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) return null;
        try (BufferedReader br = Files.newBufferedReader(p, java.nio.charset.StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) return null;
            List<String> cols = parseLine(header, delim);
            List<List<String>> rows = new ArrayList<>();
            long total = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                total++;
                if (rows.size() < 10) rows.add(parseLine(line, delim));
            }
            List<String> types = new ArrayList<>();
            for (int c = 0; c < cols.size(); c++) types.add(inferType(rows, c));
            return new FilePreview(cols, types, rows, total);
        } catch (IOException | RuntimeException e) {
            return null;   // binary or non-UTF-8 file: no preview
        }
    }

    static List<String> parseLine(String line, char delim) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else cur.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == delim) { out.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static String inferType(List<List<String>> rows, int col) {
        boolean any = false, allInt = true, allNum = true;
        for (List<String> r : rows) {
            if (col >= r.size() || r.get(col).isEmpty()) continue;
            any = true;
            String v = r.get(col);
            try { Long.parseLong(v); } catch (NumberFormatException e) { allInt = false; }
            try { Double.parseDouble(v); } catch (NumberFormatException e) { allNum = false; }
        }
        if (!any) return "empty";
        return allInt ? "integer" : allNum ? "decimal" : "text";
    }
}

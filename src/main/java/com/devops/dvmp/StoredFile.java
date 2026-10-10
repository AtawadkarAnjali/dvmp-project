package com.devops.dvmp;

public record StoredFile(String originalName, String storedName, long size, String checksum, String format) { }

package com.devops.dvmp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class DatasetVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int versionNo;
    @Column(length = 500)
    private String notes;
    private String fileName;
    private String storedName;
    private long fileSize;
    private String checksum;
    private String createdBy;
    private Integer downloads = 0;
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    private Dataset dataset;

    public DatasetVersion() {}
    public DatasetVersion(int versionNo, String notes, StoredFile f, String createdBy) {
        this.versionNo = versionNo; this.notes = notes; this.createdBy = createdBy;
        this.fileName = f.originalName(); this.storedName = f.storedName();
        this.fileSize = f.size(); this.checksum = f.checksum();
    }

    public String getSizeLabel() {
        if (fileSize <= 0) return "-";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / 1048576.0);
    }
    public String getShortChecksum() { return checksum == null ? "-" : checksum.substring(0, 12); }

    public int getDownloads() { return downloads == null ? 0 : downloads; }
    public void addDownload() { downloads = getDownloads() + 1; }

    public Long getId() { return id; }
    public int getVersionNo() { return versionNo; }
    public String getNotes() { return notes; }
    public String getFileName() { return fileName; }
    public String getStoredName() { return storedName; }
    public long getFileSize() { return fileSize; }
    public String getChecksum() { return checksum; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
}

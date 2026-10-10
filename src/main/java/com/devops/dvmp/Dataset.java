package com.devops.dvmp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
public class Dataset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(length = 200)
    private String subtitle;
    @Column(length = 2000)
    private String description;
    @Column(length = 250)
    private String tags;          // comma separated, lower case
    private String license;
    private String format;
    private String owner;
    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;
    @Column(length = 500)
    private String reviewNote;
    private String reviewedBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo ASC")
    private List<DatasetVersion> versions = new ArrayList<>();

    public Dataset() {}
    public Dataset(String name, String description, String format, String owner) {
        this.name = name; this.description = description; this.format = format; this.owner = owner;
    }

    public void addVersion(DatasetVersion v) { v.setDataset(this); versions.add(v); }

    // ---------- derived values used by the pages ----------
    public DatasetVersion getLatest() { return versions.isEmpty() ? null : versions.get(versions.size() - 1); }
    public int getCurrentVersion() { return versions.isEmpty() ? 0 : getLatest().getVersionNo(); }
    public String getLatestSize() { return getLatest() == null ? "-" : getLatest().getSizeLabel(); }
    public LocalDateTime getLastUpdated() { return getLatest() == null ? createdAt : getLatest().getCreatedAt(); }
    public int getTotalDownloads() { return versions.stream().mapToInt(DatasetVersion::getDownloads).sum(); }
    public List<String> getTagList() {
        return (tags == null || tags.isBlank()) ? List.of() : Arrays.asList(tags.split(","));
    }

    /** Kaggle-style completeness score out of 10. */
    public String getUsabilityScore() {
        double s = 0;
        int dl = description == null ? 0 : description.trim().length();
        if (dl >= 80) s += 3; else if (dl >= 20) s += 1.5;
        if (subtitle != null && subtitle.trim().length() >= 10) s += 1;
        if (!getTagList().isEmpty()) s += 2;
        if (license != null && !license.isBlank() && !license.equals("Unknown")) s += 2;
        if (getLatest() != null && getLatest().getStoredName() != null) s += 1;
        if (status == Status.APPROVED) s += 1;
        return String.format(Locale.ROOT, "%.1f", s);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getOwner() { return owner; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<DatasetVersion> getVersions() { return versions; }
}

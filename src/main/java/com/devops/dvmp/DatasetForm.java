package com.devops.dvmp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DatasetForm {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 150, message = "Subtitle must be at most 150 characters")
    private String subtitle;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Size(max = 200, message = "Tags must be at most 200 characters")
    private String tags;

    private String license = "Unknown";

    public DatasetForm() {}
    public DatasetForm(Dataset d) {
        this.name = d.getName(); this.subtitle = d.getSubtitle(); this.description = d.getDescription();
        this.tags = d.getTags(); this.license = d.getLicense() == null ? "Unknown" : d.getLicense();
    }

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
}

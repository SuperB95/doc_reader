package com.example.doc_reader.model;

public class Document {
    private String id;
    private String title;
    private String description;
    private String fileUrl;
    private long timestamp;
    private String ownerId;

    public Document() {}

    public Document(String title, String description, String fileUrl, long timestamp, String ownerId) {
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.timestamp = timestamp;
        this.ownerId = ownerId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
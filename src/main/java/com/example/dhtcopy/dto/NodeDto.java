package com.example.dhtcopy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public class NodeDto {
    @NotBlank(message = "Node ID cannot be blank")
    private String id;

    @NotBlank(message = "Host cannot be blank")
    private String host;

    @Min(value = 1024, message = "Port must be greater than 1024")
    @Max(value = 65535, message = "Port must be less than 65536")
    private int port;

    private boolean active = true;
    private int dataSize;
    private long operationCount;

    // Constructors
    public NodeDto() {}

    public NodeDto(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getDataSize() { return dataSize; }
    public void setDataSize(int dataSize) { this.dataSize = dataSize; }

    public long getOperationCount() { return operationCount; }
    public void setOperationCount(long operationCount) { this.operationCount = operationCount; }
}


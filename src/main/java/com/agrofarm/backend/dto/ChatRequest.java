package com.agrofarm.backend.dto;


public class ChatRequest {
    private String polygonId;
    private String message;

    public String getPolygonId() {
        return polygonId;
    }
    public void setPolygonId(String polygonId) {
        this.polygonId = polygonId;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
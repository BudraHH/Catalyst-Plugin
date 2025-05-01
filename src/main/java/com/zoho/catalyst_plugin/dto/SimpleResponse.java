package com.zoho.catalyst_plugin.dto;

public class SimpleResponse {
    public String message;

    public SimpleResponse() {
    }

    public SimpleResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "SimpleResponse{" +
                "message='" + message + '\'' +
                '}';
    }
}
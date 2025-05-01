package com.zoho.catalyst_plugin.dto;

public class ApiResponse {
    public String message;
    public String data;
    public String error;

    public ApiResponse() {}

    // For ERROR
    public ApiResponse(String error) {
        this.error = error;
        this.message = null;
        this.data = null;
    }

    // For SUCCESS
    public ApiResponse(String message, String data) {
        this.message = message;
        this.data = data;
        this.error = null;
    }

    // --- Getters ---
    public String getMessage() {
        return message;
    }

    public String getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    // --- Setters ---
    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setError(String error) {
        this.error = error;
    }
}


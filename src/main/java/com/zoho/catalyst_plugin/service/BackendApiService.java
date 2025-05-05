package com.zoho.catalyst_plugin.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.zoho.catalyst_plugin.dto.ApiResponse;
import com.zoho.catalyst_plugin.dto.AuthResponse;

import com.zoho.catalyst_plugin.dto.SimpleResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BackendApiService {
    private static final Logger LOG = Logger.getInstance(BackendApiService.class);
    private static final BackendApiService instance = new BackendApiService();
    private final Gson gson = new Gson();

    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static final String GITHUB_EXCHANGE_ENDPOINT = API_BASE_URL + "/auth/github/exchange-code";
    private static final String RESOLVE_ENDPOINT = API_BASE_URL + "/logical-seed-key/resolve";

    private BackendApiService() {}

    public static BackendApiService getInstance() {
        return instance;
    }

    public AuthResponse exchangeGitHubCode(String authorizationCode)
            throws IOException, InterruptedException, IllegalArgumentException, JsonSyntaxException { // Added JsonSyntaxException

        LOG.info("Calling backend GitHub code exchange API.");
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new IllegalArgumentException("GitHub authorization code cannot be null or empty.");
        }

        // Prepare Request Body in JSON format
        Map<String, String> payload = new HashMap<>();
        payload.put("code", authorizationCode);
        String requestBodyJson = gson.toJson(payload);

        // --- Use Apache HttpClient 5 for HTTP reqs ---
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(GITHUB_EXCHANGE_ENDPOINT);

            // Set Headers
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());

            // Set Request Body
            httpPost.setEntity(new StringEntity(requestBodyJson, ContentType.APPLICATION_JSON));

            LOG.debug("Executing POST request to {}", GITHUB_EXCHANGE_ENDPOINT);

            // Execute and process response directly
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                String responseBody = null;
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try {
                        responseBody = EntityUtils.toString(entity);
                    } finally {
                        EntityUtils.consumeQuietly(entity); // Ensure consumed
                    }
                }

                LOG.debug("Received code exchange response status: {}, Body: {}", statusCode, responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 500)) + "..." : "null");

                if (statusCode >= 200 && statusCode < 300) {
                    // Success
                    if (responseBody != null && !responseBody.isEmpty()) {
                        try {
                            AuthResponse authResponse = gson.fromJson(responseBody, AuthResponse.class);
                            if (authResponse == null) {
                                throw new IOException("Parsed JSON response body is null for GitHub Code Exchange.");
                            }
                            LOG.info("GitHub Code Exchange successful according to backend.");
                            return authResponse; // Return parsed DTO
                        } catch (JsonSyntaxException e) {
                            LOG.error("Failed to parse successful JSON response for GitHub Code Exchange: " + responseBody, e);
                            throw new IOException("Invalid JSON format in successful response from server for GitHub Code Exchange.", e);
                        }
                    } else {
                        LOG.error("Received successful status code ({}) but empty response body for GitHub Code Exchange.", String.valueOf(statusCode));
                        throw new IOException("Empty response body on successful status " + statusCode + " for GitHub Code Exchange.");
                    }
                } else {
                    // Error status code
                    String errorMsg = "GitHub Code Exchange failed (HTTP Status: " + statusCode + ")";
                    if (responseBody != null) {
                        // Try parsing potential error DTOs (AuthResponse/SimpleResponse might contain messages)
                        String backendError = parseErrorMessage(responseBody); // Use existing helper
                        if(backendError != null) {
                            errorMsg = backendError + " (HTTP Status: " + statusCode + ")";
                        } else {
                            errorMsg += " - " + responseBody.substring(0, Math.min(responseBody.length(), 200)) + "...";
                        }
                    }
                    LOG.warn("GitHub Code Exchange API call failed: {}" + errorMsg);
                    throw new IOException(errorMsg); // Throw exception to signal failure
                }
            } // CloseableHttpResponse closed
        } // CloseableHttpClient closed
        catch (IOException e) {
            // Catch/log network or processing errors
            LOG.error("IOException during HTTP request to {}: {}", GITHUB_EXCHANGE_ENDPOINT, e.getMessage());
            throw e;
        } catch (Exception e) {
            // Catch other potential runtime exceptions
            LOG.error("Unexpected exception during HTTP request execution for code exchange", e);
            throw new IOException("Unexpected error during API call for code exchange: " + e.getMessage(), e);
        }
    }

    // --- resolveLskSelection method  ---
    public ApiResponse resolveLskSelection(String inputModuleName, String xmlContent, String sessionToken) throws IOException, IllegalArgumentException {
        LOG.info("Calling LSK Resolve API for XML selection.");

        if (sessionToken == null || sessionToken.trim().isEmpty()) { throw new IllegalArgumentException("Auth token cannot be null or empty for resolution."); }
        if (inputModuleName == null || inputModuleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name cannot be empty for resolution.");
        }
        if (xmlContent == null || xmlContent.isEmpty()) { throw new IllegalArgumentException("XML content cannot be empty for resolution."); }

        Map<String, String> requestPayload = new HashMap<>();
        requestPayload.put("moduleName", inputModuleName);
        requestPayload.put("xmlContent", xmlContent);
        String requestBodyJson = gson.toJson(requestPayload);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(RESOLVE_ENDPOINT);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()); httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()); httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken);
            httpPost.setEntity(new StringEntity(requestBodyJson, ContentType.APPLICATION_JSON));

            LOG.debug("Executing POST request to {}", RESOLVE_ENDPOINT);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode(); String responseBody = null; HttpEntity entity = response.getEntity();
                if (entity != null) { try { responseBody = EntityUtils.toString(entity); } finally { EntityUtils.consumeQuietly(entity); } }

                LOG.debug("Received response status: {}, Body: {}", statusCode, responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 500))+"..." : "null");

                if (statusCode >= 200 && statusCode < 300) {
                    if (responseBody != null) { try { ApiResponse apiResponse = gson.fromJson(responseBody, ApiResponse.class); if (apiResponse == null) { throw new IOException("Failed to parse successful response JSON from server."); } LOG.info("LSK resolution successful according to backend."); return apiResponse; } catch (JsonSyntaxException e) { LOG.error("Failed to parse successful response JSON: " + responseBody, e); throw new IOException("Invalid JSON format in successful response from server.", e); } } else { LOG.error("Received successful status code ({}) but empty response body from server.", String.valueOf(statusCode)); throw new IOException("Empty response body on successful status " + statusCode + " from server."); }
                } else {
                    String errorMsg = "LSK Resolution failed (HTTP Status: " + statusCode + ")";
                    if (responseBody != null) { String backendError = parseErrorMessage(responseBody); if (backendError != null) { errorMsg = backendError + " (HTTP Status: " + statusCode + ")"; } else { errorMsg += " - " + responseBody.substring(0, Math.min(responseBody.length(), 200)); } }

                    LOG.warn("LSK resolution failed on backend: " + errorMsg);
                    return new ApiResponse(errorMsg); // Return ApiResponse indicating error
                }
            }
        } catch (IOException e) { LOG.error("IOException during HTTP request to {}: {}", RESOLVE_ENDPOINT, e.getMessage()); throw e;
        } catch (Exception e) { LOG.error("Unexpected exception during HTTP request execution", e); throw new IOException("Unexpected error during API call: " + e.getMessage(), e); }
    }



    private String parseErrorMessage(String responseBody) {
        // If the response body is null or empty, we can't parse anything.
        if (responseBody == null || responseBody.isEmpty()) {
            LOG.debug("Cannot parse error message from null or empty response body.");
            return null;
        }

        // Try parsing as ApiResponse (checks 'error' field)
        try {
            ApiResponse apiError = gson.fromJson(responseBody, ApiResponse.class);
            // Check if parsing succeeded AND the 'error' field is present and not empty
            if (apiError != null && apiError.getError() != null && !apiError.getError().trim().isEmpty()) {
                LOG.debug("Parsed error message from ApiResponse structure.");
                return apiError.getError();
            }
        } catch (JsonSyntaxException ignored) {
            // JSON body didn't match ApiResponse structure, silently ignore and try the next format.
            LOG.trace("Response body did not match ApiResponse structure." + ignored);
        }

        // Try parsing as SimpleResponse (checks 'message' field)
        try {
            SimpleResponse simpleError = gson.fromJson(responseBody, SimpleResponse.class);
            // Check if parsing succeeded AND the 'message' field is present and not empty
            if (simpleError != null && simpleError.getMessage() != null && !simpleError.getMessage().trim().isEmpty()) {
                LOG.debug("Parsed error message from SimpleResponse structure.");
                return simpleError.getMessage();
            }
        } catch (JsonSyntaxException ignored) {
            // JSON body didn't match SimpleResponse structure, silently ignore and try the next format.
            LOG.trace("Response body did not match SimpleResponse structure."+ ignored);
        }

        // Try parsing as AuthResponse (checks 'message' field - less likely for errors)
        try {
            AuthResponse authError = gson.fromJson(responseBody, AuthResponse.class);
            // Check if parsing succeeded AND the 'message' field is present and not empty
            if (authError != null && authError.getMessage() != null && !authError.getMessage().trim().isEmpty()) {
                LOG.debug("Parsed message from AuthResponse structure (might indicate error).");
                return authError.getMessage(); // Return the message
            }
        } catch (JsonSyntaxException ignored) {
            // JSON body didn't match AuthResponse structure, silently ignore.
            LOG.trace("Response body did not match AuthResponse structure." + ignored);
        }

        LOG.debug("Could not parse a known error structure from the response body.");
        return null;
    }
}
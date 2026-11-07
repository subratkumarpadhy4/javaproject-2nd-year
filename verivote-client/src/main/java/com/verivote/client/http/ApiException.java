package com.verivote.client.http;

public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public ApiException(int statusCode, String message) {
        this(statusCode, message, null, null);
    }

    public ApiException(int statusCode, String message, String responseBody) {
        this(statusCode, message, responseBody, null);
    }

    public ApiException(int statusCode, String message, Throwable cause) {
        this(statusCode, message, null, cause);
    }

    public ApiException(int statusCode, String message, String responseBody, Throwable cause) {
        super("HTTP " + statusCode + ": " + message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

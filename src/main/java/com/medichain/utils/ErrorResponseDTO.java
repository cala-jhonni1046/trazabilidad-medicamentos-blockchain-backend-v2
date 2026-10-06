package com.medichain.utils;

import java.util.List;

public class ErrorResponseDTO {
    private int status;
    private String message;
    private String regla;
    private List<FieldError> errors;

    public ErrorResponseDTO() {
    }

    public ErrorResponseDTO(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public ErrorResponseDTO(int status, String message, String regla) {
        this.status = status;
        this.message = message;
        this.regla = regla;
    }

    public ErrorResponseDTO(int status, String message, List<FieldError> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
    }

    public ErrorResponseDTO(int status, String message, String regla, List<FieldError> errors) {
        this.status = status;
        this.message = message;
        this.regla = regla;
        this.errors = errors;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public void setErrors(List<FieldError> errors) {
        this.errors = errors;
    }

    public String getRegla() {
        return regla;
    }

    public void setRegla(String regla) {
        this.regla = regla;
    }

    public static class FieldError {
        private String field;
        private String message;

        public FieldError() {
        }

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

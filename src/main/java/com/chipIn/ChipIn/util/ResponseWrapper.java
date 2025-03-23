package com.chipIn.ChipIn.util;

import lombok.Data;

@Data
public class ResponseWrapper <T>{

    public String message;
    public boolean success;
    public T data;

    public ResponseWrapper(String message, boolean success, T data) {
        this.message = message;
        this.success = success;
        this.data = data;
    }

    public ResponseWrapper(String message, boolean success) {
        this.message = message;
        this.success = success;
    }

    public static <T> ResponseWrapper<T> success(T data) {
        return new ResponseWrapper<>("Success", true, data);
    }

    public static <T> ResponseWrapper<T> failure(String message, T data) {
        return new ResponseWrapper<>(message, false, data);
    }

}

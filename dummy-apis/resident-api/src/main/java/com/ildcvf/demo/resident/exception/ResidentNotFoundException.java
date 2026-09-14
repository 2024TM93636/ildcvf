package com.ildcvf.demo.resident.exception;

public class ResidentNotFoundException extends RuntimeException {

    public ResidentNotFoundException(Long id) {
        super("Resident not found with id: " + id);
    }
}
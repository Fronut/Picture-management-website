package com.imagemanagement.exception;

import java.util.List;

public class DuplicateFileException extends BadRequestException {

    private final List<String> duplicates;

    public DuplicateFileException(List<String> duplicates, String message) {
        super(message);
        this.duplicates = duplicates;
    }

    public List<String> getDuplicates() {
        return duplicates;
    }
}

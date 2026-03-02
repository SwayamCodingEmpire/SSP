package com.isekai.ssp.service;

import com.isekai.ssp.helpers.FileFormat;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for extracting text from document files.
 * Implementations handle format-specific parsing (PDF, EPUB, DOCX, TXT).
 *
 * This interface allows swapping the underlying extraction library
 * (Tika, PDFBox, Docling, etc.) without changing business logic.
 */
public interface DocumentTextExtractor {

    /**
     * Extracts plain text content from a document file.
     *
     * @param inputStream The document file input stream
     * @param format      The document format
     * @return Extracted text with preserved paragraph structure
     * @throws DocumentExtractionException if extraction fails
     */
    String extractText(InputStream inputStream, FileFormat format) throws DocumentExtractionException;

    /**
     * Checks if this extractor supports the given format.
     *
     * @param format The document format
     * @return true if the format is supported
     */
    boolean supports(FileFormat format);

    /**
     * Exception thrown when document text extraction fails.
     */
    class DocumentExtractionException extends RuntimeException {
        public DocumentExtractionException(String message) {
            super(message);
        }

        public DocumentExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
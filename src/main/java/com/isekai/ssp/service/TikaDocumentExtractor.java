package com.isekai.ssp.service;

import com.isekai.ssp.helpers.FileFormat;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Apache Tika-based document text extractor.
 * Handles PDF, EPUB, DOCX, TXT and any other format Tika supports.
 *
 * Tika wraps PDFBox (for PDF), Apache POI (for DOCX), and other parsers
 * behind a single unified API — one implementation covers all formats.
 */
@Service
public class TikaDocumentExtractor implements DocumentTextExtractor {

    private static final Set<FileFormat> SUPPORTED_FORMATS =
            Set.of(FileFormat.PDF, FileFormat.EPUB, FileFormat.DOCX, FileFormat.TXT);

    private final Tika tika;

    public TikaDocumentExtractor() {
        this.tika = new Tika();
        // Default max string length is 100k chars — novels can be much longer
        this.tika.setMaxStringLength(-1); // unlimited
    }

    @Override
    public String extractText(InputStream inputStream, FileFormat format) throws DocumentExtractionException {
        if (!supports(format)) {
            throw new DocumentExtractionException("Unsupported format: " + format);
        }

        try {
            Metadata metadata = new Metadata();
            // Hint the MIME type so Tika picks the right parser immediately
            metadata.set(Metadata.CONTENT_TYPE, mimeTypeFor(format));

            String extractedText = tika.parseToString(inputStream, metadata);

            if (extractedText == null || extractedText.isBlank()) {
                throw new DocumentExtractionException(
                        "No text content extracted from " + format + " file. " +
                        "The file may be image-based or contain only scanned pages.");
            }

            return extractedText.strip();

        } catch (TikaException e) {
            throw new DocumentExtractionException(
                    "Failed to parse " + format + " file: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new DocumentExtractionException(
                    "Failed to read " + format + " file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(FileFormat format) {
        return SUPPORTED_FORMATS.contains(format);
    }

    /**
     * Maps FileFormat to MIME type for Tika parser selection.
     */
    private String mimeTypeFor(FileFormat format) {
        return switch (format) {
            case PDF -> "application/pdf";
            case EPUB -> "application/epub+zip";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case TXT -> "text/plain";
        };
    }
}
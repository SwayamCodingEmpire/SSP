package com.isekai.ssp.dto;

import java.util.List;

public record FormAnalysisResult(
        String form,
        String meterPattern,
        String rhymeScheme,
        int stanzaCount,
        Integer linesPerStanza,
        List<String> soundDevices,
        String notes
) {}

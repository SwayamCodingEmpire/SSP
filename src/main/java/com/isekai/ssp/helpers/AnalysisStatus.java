package com.isekai.ssp.helpers;

public enum AnalysisStatus {
    PENDING,    // chapter uploaded, analysis not yet triggered
    ANALYZING,  // AI pipeline is running
    ANALYZED,   // pipeline completed successfully
    FAILED      // pipeline errored
}
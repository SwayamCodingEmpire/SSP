package com.isekai.ssp.helpers;

/**
 * Classifies the temporal position of a scene in the story's timeline.
 * Used to retrieve the correct character state snapshot during translation.
 *
 * PRESENT      — the main narrative timeline
 * FLASHBACK    — a scene set before the current narrative present
 * FLASH_FORWARD — a scene set after the current narrative present
 */
public enum NarrativeTimeType {
    PRESENT,
    FLASHBACK,
    FLASH_FORWARD
}
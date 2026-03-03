package com.isekai.ssp.dto;

/**
 * Request body for saving the user's reviewed/edited translation.
 *
 * @param editedText  Required only when accepted=false. The user's modified text.
 *                    Ignored (and not stored) when accepted=true — AI output is the final version.
 * @param accepted    true  = user accepted AI output as-is (editedText not needed).
 *                    false = user modified the text (editedText must be provided).
 */
public record SaveTranslationRequest(
        String editedText,
        boolean accepted
) {}

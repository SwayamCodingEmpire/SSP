package com.isekai.ssp.helpers;

public enum ContentType {
    // Narrative Fiction family
    NOVEL(ContentFamily.NARRATIVE_FICTION),
    FICTION(ContentFamily.NARRATIVE_FICTION),
    SHORT_STORY(ContentFamily.NARRATIVE_FICTION),
    CLASSIC_LITERATURE(ContentFamily.NARRATIVE_FICTION),
    MODERN_LITERATURE(ContentFamily.NARRATIVE_FICTION),

    // Non-Fiction Prose family
    NON_FICTION(ContentFamily.NON_FICTION_PROSE),
    ESSAY(ContentFamily.NON_FICTION_PROSE),
    BOOK(ContentFamily.NON_FICTION_PROSE),

    // Academic/Technical family
    EDUCATIONAL_TEXTBOOK(ContentFamily.ACADEMIC),
    JOURNAL(ContentFamily.ACADEMIC),
    ARTICLE(ContentFamily.ACADEMIC),

    // Poetic/Lyrical family
    POETRY(ContentFamily.POETIC),
    SONG_LYRICS(ContentFamily.POETIC),

    // Dramatic/Script family
    TV_MOVIE_SCRIPT(ContentFamily.DRAMATIC),

    // Periodical family
    MAGAZINE(ContentFamily.PERIODICAL);

    private final ContentFamily family;

    ContentType(ContentFamily family) {
        this.family = family;
    }

    public ContentFamily getFamily() {
        return family;
    }
}

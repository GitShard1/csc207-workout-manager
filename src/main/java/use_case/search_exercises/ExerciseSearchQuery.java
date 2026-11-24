package use_case.search_exercises;

import java.util.Locale;

import entity.BodyPart;

/**
 * What to search the exercise library for. Every part is optional: a {@code null} part means
 * "don't filter on this".
 *
 * <p>The name and equipment are trimmed and lower-cased, and blank text becomes {@code null}, so
 * searches that differ only in letter case or surrounding spaces are equal. Every data-access
 * implementation receives the same cleaned-up query, and equal queries can share a map key.
 *
 * @param name      part of the exercise name, such as "press", or {@code null} for any
 * @param bodyPart  the body part to keep, or {@code null} for any
 * @param equipment the equipment to keep, such as "barbell", or {@code null} for any
 */
public record ExerciseSearchQuery(String name, BodyPart bodyPart, String equipment) {

    /**
     * Creates a query, cleaning up the name and equipment as described above.
     *
     * @param name      part of the exercise name; may be blank or {@code null}
     * @param bodyPart  the body part to keep, or {@code null} for any
     * @param equipment the equipment to keep; may be blank or {@code null}
     */
    public ExerciseSearchQuery {
        name = normalize(name);
        equipment = normalize(equipment);
    }

    /**
     * Tells whether the query filters on nothing at all.
     *
     * @return {@code true} if no name, body part or equipment is given
     */
    public boolean isEmpty() {
        return name == null && bodyPart == null && equipment == null;
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip().toLowerCase(Locale.ROOT);
    }
}

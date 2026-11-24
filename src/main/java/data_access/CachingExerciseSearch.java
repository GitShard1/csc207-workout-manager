package data_access;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import entity.Exercise;
import use_case.search_exercises.ExerciseLibraryUnavailableException;
import use_case.search_exercises.ExerciseSearchDataAccessInterface;
import use_case.search_exercises.ExerciseSearchQuery;

/**
 * Remembers search results for the rest of the run, so repeating a search sends no new request.
 * It wraps another exercise search and looks the same from outside (the Decorator pattern), so
 * the use case can't tell whether it is talking to the cache or to the real library.
 *
 * <p>Equal queries share results; {@link ExerciseSearchQuery} already ignores letter case and
 * surrounding spaces. Failures are never remembered: the next try asks the wrapped search again.
 */
public class CachingExerciseSearch implements ExerciseSearchDataAccessInterface {
    private final ExerciseSearchDataAccessInterface wrapped;
    // shortcut: unbounded and lost on exit; fine for one session's searches, add a size limit if memory grows
    private final Map<ExerciseSearchQuery, List<Exercise>> resultsByQuery = new HashMap<>();

    /**
     * Creates a cache in front of another exercise search.
     *
     * @param wrapped the search to ask when a query isn't cached yet
     */
    public CachingExerciseSearch(ExerciseSearchDataAccessInterface wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Returns the cached results for this query, or asks the wrapped search and caches its answer.
     *
     * @param query what to search for
     * @return the matching exercises; the list cannot be modified, so callers can't change the cache
     * @throws ExerciseLibraryUnavailableException if the query isn't cached and the wrapped search fails
     */
    @Override
    public List<Exercise> search(ExerciseSearchQuery query) throws ExerciseLibraryUnavailableException {
        List<Exercise> cached = resultsByQuery.get(query);
        if (cached != null) {
            return cached;
        }
        List<Exercise> results = List.copyOf(wrapped.search(query));
        resultsByQuery.put(query, results);
        return results;
    }
}

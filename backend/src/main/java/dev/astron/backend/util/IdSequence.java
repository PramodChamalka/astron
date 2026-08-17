package dev.astron.backend.util;

import dev.astron.backend.model.Counter;
import dev.astron.backend.repository.CounterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hands out the next number in an id series like TASK-101 or ASG-001.
 *
 * WHY THIS EXISTS: ids used to be generated from a document COUNT, e.g.
 * "TASK-" + (101 + taskRepo.count()). That reuses ids after a delete -
 * delete TASK-103 and the next task created becomes TASK-103 again. Any
 * assignment or audit record still naming the old TASK-103 would then
 * silently attach itself to a completely different task.
 *
 * Taking "the highest id that currently exists, plus one" is NOT enough
 * on its own: delete the HIGHEST task and that maximum drops back, so
 * the id is reused anyway. The high-water mark therefore lives in its
 * own counters collection, which only ever moves up.
 *
 * The existing documents are still consulted as a FLOOR, so a database
 * that already contains TASK-104 (created before this class existed)
 * can never be handed TASK-104 again.
 */
@Component
public class IdSequence {

    @Autowired private CounterRepository counterRepo;

    /**
     * Reserve and return the next number in a series.
     *
     * @param series      counter name, e.g. "tasks"
     * @param existingIds ids already present, used only as a floor
     * @param prefix      the part before the number, e.g. "TASK-"
     * @param startAt     first number this series should ever use
     */
    public int next(String series, List<String> existingIds,
                    String prefix, int startAt) {

        // Floor = the highest id actually present, or one below the
        // series start if the collection is empty. This is what makes
        // the very first task come out as TASK-101.
        int floor = Math.max(highestNumber(existingIds, prefix), startAt - 1);

        Counter counter = counterRepo.findById(series)
            .orElseGet(() -> new Counter(series, 0));

        // Take whichever is further along - the counter or the floor -
        // and step past it. The counter can never move backwards.
        int next = Math.max(counter.getSeq(), floor) + 1;

        counter.setSeq(next);
        counterRepo.save(counter);
        return next;
    }

    // The largest number currently in use across the given ids, or 0.
    private static int highestNumber(List<String> existingIds, String prefix) {
        int highest = 0;
        for (String id : existingIds) {
            int number = numberIn(id, prefix);
            if (number > highest) highest = number;
        }
        return highest;
    }

    // Pull the number out of one id. Anything that isn't "prefix + digits"
    // is ignored rather than allowed to blow up id generation - a single
    // hand-edited document in Mongo shouldn't stop new tasks being made.
    private static int numberIn(String id, String prefix) {
        if (id == null || !id.startsWith(prefix)) return 0;
        try {
            // Integer.parseInt copes with the zero padding in "ASG-001".
            return Integer.parseInt(id.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

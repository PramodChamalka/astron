package dev.astron.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One row per id series ("tasks", "assignments"), remembering the
 * highest number ever issued for it.
 *
 * This has to live in the database rather than be derived from the
 * tasks/assignments themselves: if the highest-numbered task is
 * deleted, a "max of what exists" calculation drops back and hands
 * the same id out again. This counter never goes down.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "counters")
public class Counter {

    // The series name, e.g. "tasks". Used directly as the mongo _id so
    // there can only ever be one counter per series.
    @Id
    private String id;

    private int seq;
}

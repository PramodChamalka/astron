package dev.astron.backend.controller;

import dev.astron.backend.model.Developer;
import dev.astron.backend.repository.DeveloperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

// @RestController tells Spring: this class handles HTTP requests, and
// every method's return value should be converted straight to JSON
// (instead of, say, an HTML page).
@RestController
// @RequestMapping sets the base URL path for every endpoint in this
// class. So the method below actually answers GET /api/developers.
@RequestMapping("/api/developers")
public class DeveloperController {

    // @Autowired asks Spring to automatically supply (inject) an
    // already-built DeveloperRepository here, so we don't have to
    // construct one ourselves with "new".
    @Autowired
    private DeveloperRepository repo;

    // @GetMapping (with no path) means this method runs when someone
    // sends a GET request to exactly /api/developers.
    @GetMapping
    public Map<String, Object> getAll() {
        // findAll() comes from MongoRepository - it fetches every
        // document in the "developers" collection as Developer objects.
        List<Developer> devs = repo.findAll();

        // Map.of(...) builds a small JSON-like response:
        // { "success": true, "data": [ ...developers... ] }
        return Map.of("success", true, "data", devs);
    }
}

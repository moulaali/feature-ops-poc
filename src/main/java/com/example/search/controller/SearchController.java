package com.example.search.controller;

import com.example.search.model.SearchResponse;
import com.example.search.service.SearchRoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class SearchController {

    private final SearchRoutingService searchRoutingService;

    public SearchController(SearchRoutingService searchRoutingService) {
        this.searchRoutingService = searchRoutingService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "userId", required = false) String userId
    ) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be provided and not blank");
        }

        SearchResponse response = searchRoutingService.executeSearch(query, userId.trim());
        return ResponseEntity.ok(response);
    }
}

package com.example.search.model;

public record SearchResponse(
        String query,
        String userId,
        String implementation,
        String message
) {}

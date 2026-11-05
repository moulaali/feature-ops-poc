package com.example.search.service;

import com.example.search.model.SearchResponse;

public interface SearchEngine {
    SearchResponse search(String query, String userId);
}

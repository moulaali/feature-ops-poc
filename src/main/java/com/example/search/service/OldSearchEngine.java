package com.example.search.service;

import com.example.search.model.SearchResponse;
import org.springframework.stereotype.Service;

@Service("oldSearchEngine")
public class OldSearchEngine implements SearchEngine {

    @Override
    public SearchResponse search(String query, String userId) {
        return new SearchResponse(query, userId, "v1", "Results from OLD search engine");
    }
}

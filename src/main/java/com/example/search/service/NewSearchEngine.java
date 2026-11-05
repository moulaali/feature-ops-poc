package com.example.search.service;

import com.example.search.model.SearchResponse;
import org.springframework.stereotype.Service;

@Service("newSearchEngine")
public class NewSearchEngine implements SearchEngine {

    @Override
    public SearchResponse search(String query, String userId) {
        return new SearchResponse(query, userId, "v2", "Results from NEW search engine");
    }
}

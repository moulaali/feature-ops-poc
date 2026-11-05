package com.example.search.service;

import com.example.search.model.SearchResponse;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SearchRoutingService {

    public static final String SEARCH_V2_FLAG = "search-v2";

    private static final Logger log = LoggerFactory.getLogger(SearchRoutingService.class);

    private final Unleash unleash;
    private final SearchEngine oldSearchEngine;
    private final SearchEngine newSearchEngine;

    public SearchRoutingService(
            Unleash unleash,
            @Qualifier("oldSearchEngine") SearchEngine oldSearchEngine,
            @Qualifier("newSearchEngine") SearchEngine newSearchEngine
    ) {
        this.unleash = unleash;
        this.oldSearchEngine = oldSearchEngine;
        this.newSearchEngine = newSearchEngine;
    }

    public SearchResponse executeSearch(String query, String userId) {
        UnleashContext context = UnleashContext.builder()
                .userId(userId)
                .build();

        boolean isSearchV2Enabled = unleash.isEnabled(SEARCH_V2_FLAG, context, false);

        if (isSearchV2Enabled) {
            log.debug("search-v2 ENABLED for userId={}, routing to V2", userId);
            return newSearchEngine.search(query, userId);
        } else {
            log.debug("search-v2 DISABLED for userId={}, routing to V1", userId);
            return oldSearchEngine.search(query, userId);
        }
    }
}

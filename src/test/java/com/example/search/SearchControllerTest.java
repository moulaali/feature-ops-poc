package com.example.search;

import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Unleash unleash;

    private static ArgumentMatcher<UnleashContext> matchesUserId(String expectedUserId) {
        return context -> context != null && expectedUserId.equals(context.getUserId().orElse(null));
    }

    @Test
    @DisplayName("Should return V1 when search-v2 flag evaluates to false")
    void testSearchV1WhenFlagIsDisabled() throws Exception {
        when(unleash.isEnabled(eq("search-v2"), argThat(matchesUserId("alice")), eq(false)))
                .thenReturn(false);

        mockMvc.perform(get("/search")
                        .param("q", "hello")
                        .param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("hello"))
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.implementation").value("v1"))
                .andExpect(jsonPath("$.message").value("Results from OLD search engine"));
    }

    @Test
    @DisplayName("Should return V2 when search-v2 flag evaluates to true")
    void testSearchV2WhenFlagIsEnabled() throws Exception {
        when(unleash.isEnabled(eq("search-v2"), argThat(matchesUserId("bob")), eq(false)))
                .thenReturn(true);

        mockMvc.perform(get("/search")
                        .param("q", "test-query")
                        .param("userId", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("test-query"))
                .andExpect(jsonPath("$.userId").value("bob"))
                .andExpect(jsonPath("$.implementation").value("v2"))
                .andExpect(jsonPath("$.message").value("Results from NEW search engine"));
    }

    @Test
    @DisplayName("Should reject request with 400 when userId is missing or blank")
    void testRejectMissingOrBlankUserId() throws Exception {
        mockMvc.perform(get("/search").param("q", "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId must be provided and not blank"));

        mockMvc.perform(get("/search").param("q", "hello").param("userId", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId must be provided and not blank"));
    }
}

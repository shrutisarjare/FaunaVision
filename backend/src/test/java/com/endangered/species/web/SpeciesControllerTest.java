package com.endangered.species.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.endangered.species.service.SpeciesCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpeciesController.class)
class SpeciesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SpeciesCatalogService catalog;

    @Test
    void searchKeepsTheSnakeCaseContractTheFrontendExpects() throws Exception {
        given(catalog.search("tiger")).willReturn(List.of(new Dtos.SpeciesSummary(
                "Bengal Tiger", "Panthera tigris tigris", "Sundarbans National Park",
                "Endangered", "https://example.test/tiger.jpg")));

        mvc.perform(get("/search/tiger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bengal Tiger"))
                .andExpect(jsonPath("$[0].scientific_name").value("Panthera tigris tigris"))
                .andExpect(jsonPath("$[0].location").value("Sundarbans National Park"))
                .andExpect(jsonPath("$[0].status").value("Endangered"));
    }

    @Test
    void analysisPassesServiceErrorsThroughAsA200Body() throws Exception {
        given(catalog.analysis(eq("india"), eq("terrestrial"), eq("assam"), eq("tiger")))
                .willReturn(new Dtos.ErrorResponse("Species not found"));

        mvc.perform(get("/analysis/india/terrestrial/assam/tiger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Species not found"));
    }

    @Test
    void detectionLocationsReturnAPlainStringArray() throws Exception {
        given(catalog.locations("tiger", "india")).willReturn(List.of("assam", "west bengal"));

        mvc.perform(get("/detection-locations/tiger/india"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("assam"))
                .andExpect(jsonPath("$[1]").value("west bengal"));
    }
}

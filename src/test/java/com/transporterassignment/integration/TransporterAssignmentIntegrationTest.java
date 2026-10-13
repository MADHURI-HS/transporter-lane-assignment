package com.transporterassignment.integration;

import com.transporterassignment.config.OpenApiExamples;
import com.transporterassignment.repository.LaneQuoteRepository;
import com.transporterassignment.repository.LaneRepository;
import com.transporterassignment.repository.TransporterRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through the real controller, service, solver and an in-memory H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
class TransporterAssignmentIntegrationTest {

    private static final String TEST_CASE_1 = OpenApiExamples.TEST_CASE_1_INPUT;

    private static final String TEST_CASE_2 = """
            {
              "lanes": [
                {"id": 1, "origin": "Chandigarh", "destination": "Shimla"},
                {"id": 2, "origin": "Agra", "destination": "Kanpur"},
                {"id": 3, "origin": "Varanasi", "destination": "Gorakhpur"},
                {"id": 4, "origin": "Amritsar", "destination": "Ludhiana"},
                {"id": 5, "origin": "Coimbatore", "destination": "Madurai"}
              ],
              "transporters": [
                {"id": 1, "name": "Transporter X", "laneQuotes": [{"laneId": 1, "quote": 1000}, {"laneId": 2, "quote": 1500}]},
                {"id": 2, "name": "Transporter Y", "laneQuotes": [{"laneId": 3, "quote": 2000}, {"laneId": 4, "quote": 1800}]},
                {"id": 3, "name": "Transporter Z", "laneQuotes": [{"laneId": 5, "quote": 1200}]},
                {"id": 4, "name": "Transporter W", "laneQuotes": [{"laneId": 1, "quote": 1100}, {"laneId": 4, "quote": 1750}]}
              ]
            }""";

    @Autowired private MockMvc mockMvc;
    @Autowired private LaneQuoteRepository laneQuoteRepository;
    @Autowired private TransporterRepository transporterRepository;
    @Autowired private LaneRepository laneRepository;

    @BeforeEach
    void clearDatabase() {
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();
    }

    private void submit(String json) throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Input data saved successfully."));
    }

    private org.springframework.test.web.servlet.ResultActions assign(int max) throws Exception {
        return mockMvc.perform(post("/api/v1/transporters/assignment")
                .contentType(MediaType.APPLICATION_JSON).content("{\"maxTransporters\": " + max + "}"));
    }

    @Test
    @DisplayName("sample test case 1, max 3: true minimum cost 60139 using transporters 1, 4 and 5")
    void testCase1() throws Exception {
        submit(TEST_CASE_1);

        assign(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.totalCost").value(60139))
                .andExpect(jsonPath("$.selectedTransporters").value(Matchers.contains(1, 4, 5)))
                .andExpect(jsonPath("$.assignments.length()").value(5))
                .andExpect(jsonPath("$.assignments[0].laneId").value(1))
                .andExpect(jsonPath("$.assignments[0].transporterId").value(5))
                .andExpect(jsonPath("$.assignments[1].transporterId").value(1))
                .andExpect(jsonPath("$.assignments[2].transporterId").value(4))
                .andExpect(jsonPath("$.assignments[3].transporterId").value(4))
                .andExpect(jsonPath("$.assignments[4].transporterId").value(4));
    }

    @Test
    @DisplayName("sample test case 2: 3 transporters cost 7500, 4 transporters cost 7450")
    void testCase2() throws Exception {
        submit(TEST_CASE_2);

        assign(3).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7500))
                .andExpect(jsonPath("$.selectedTransporters").value(Matchers.contains(1, 2, 3)));

        assign(4).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7450))
                .andExpect(jsonPath("$.selectedTransporters").value(Matchers.contains(1, 2, 3, 4)));
    }

    @Test
    @DisplayName("sample test case 2 with max 2 cannot cover every lane: 422 saying 3 are needed")
    void testCase2NotEnoughTransporters() throws Exception {
        submit(TEST_CASE_2);

        assign(2).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("at least 3 transporters")));
    }

    @Test
    @DisplayName("assignment before any input returns 409")
    void assignmentBeforeInput() throws Exception {
        assign(3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("submitting new input replaces the earlier data set")
    void inputReplacesPreviousData() throws Exception {
        submit(TEST_CASE_1);
        submit(TEST_CASE_2);

        assign(3).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7500));
    }

    @Test
    @DisplayName("a quote for an unknown lane is rejected with a helpful message")
    void unknownLaneInQuote() throws Exception {
        String body = """
                {
                  "lanes": [{"id": 1, "origin": "Mumbai", "destination": "Delhi"}],
                  "transporters": [{"id": 1, "name": "T1", "laneQuotes": [{"laneId": 99, "quote": 100}]}]
                }""";

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Transporter 1 quoted for unknown lane 99"));
    }

    @Test
    @DisplayName("maxTransporters of 0 is rejected")
    void invalidMax() throws Exception {
        submit(TEST_CASE_1);

        assign(0).andExpect(status().isBadRequest());
    }
}

package ae.sharjah.srta.gateway;

import ae.sharjah.srta.gateway.service.SrtaProxyService;
import ae.sharjah.srta.gateway.web.SrtaProxyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the dynamic controller maps every SRTA /deg/** URL and extracts the
 * correct sub-path, using a recording stub instead of Mockito.
 */
class SrtaProxyControllerTest {

    private final AtomicReference<String> capturedSubPath = new AtomicReference<>();
    private final AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SrtaProxyService recording = new SrtaProxyService(null, null, null) {
            @Override
            public ResponseEntity<byte[]> forward(String subPath, HttpMethod method, String queryString,
                                                  HttpHeaders headers, byte[] body) {
                capturedSubPath.set(subPath);
                capturedMethod.set(method);
                return ResponseEntity.ok(("handled:" + subPath).getBytes());
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new SrtaProxyController(recording)).build();
    }

    @Test
    void mapsLookup() throws Exception {
        mockMvc.perform(post("/deg/lookup").content("{}".getBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("handled:/deg/lookup"));
        assertEquals("/deg/lookup", capturedSubPath.get());
        assertEquals(HttpMethod.POST, capturedMethod.get());
    }

    @Test
    void mapsTaxiComplaintDetails() throws Exception {
        mockMvc.perform(post("/deg/taxi-complaint-details").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/deg/taxi-complaint-details", capturedSubPath.get());
    }

    @Test
    void mapsLostComplaintDetails() throws Exception {
        mockMvc.perform(post("/deg/lost-complaint-details").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/deg/lost-complaint-details", capturedSubPath.get());
    }

    @Test
    void mapsRoadComplaintDetails() throws Exception {
        mockMvc.perform(post("/deg/road-complaint-details").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/deg/road-complaint-details", capturedSubPath.get());
    }
}

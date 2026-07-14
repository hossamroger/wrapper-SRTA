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
 * Verifies the controller maps every native SRTA path and extracts the correct
 * sub-path, using a recording stub instead of Mockito.
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
    void mapsLockup() throws Exception {
        mockMvc.perform(post("/Lockup").content("{}".getBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("handled:/Lockup"));
        assertEquals("/Lockup", capturedSubPath.get());
        assertEquals(HttpMethod.POST, capturedMethod.get());
    }

    @Test
    void mapsTaxiComplaintDetails() throws Exception {
        mockMvc.perform(post("/TaxiComplaintDetails").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/TaxiComplaintDetails", capturedSubPath.get());
    }

    @Test
    void mapsLostComplaintDetails() throws Exception {
        mockMvc.perform(post("/LostComplaintDetails").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/LostComplaintDetails", capturedSubPath.get());
    }

    @Test
    void mapsRoadComplaintDetails() throws Exception {
        mockMvc.perform(post("/RoadComplaintDetails").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/RoadComplaintDetails", capturedSubPath.get());
    }
}

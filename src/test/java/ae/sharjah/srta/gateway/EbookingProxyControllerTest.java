package ae.sharjah.srta.gateway;

import ae.sharjah.srta.gateway.service.SrtaProxyService;
import ae.sharjah.srta.gateway.web.EbookingProxyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the ebooking controller maps the native taxidispatch paths and routes
 * them through {@code forwardEbooking}, using a recording stub.
 */
class EbookingProxyControllerTest {

    private final AtomicReference<String> capturedSubPath = new AtomicReference<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SrtaProxyService recording = new SrtaProxyService(null, null, null) {
            @Override
            public ResponseEntity<byte[]> forwardEbooking(String subPath, HttpMethod method, String queryString,
                                                          HttpHeaders headers, byte[] body) {
                capturedSubPath.set(subPath);
                return ResponseEntity.ok(("ebooking:" + subPath).getBytes());
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new EbookingProxyController(recording)).build();
    }

    @Test
    void mapsBook() throws Exception {
        mockMvc.perform(post("/book").content("{}".getBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("ebooking:/book"));
        assertEquals("/book", capturedSubPath.get());
    }

    @Test
    void mapsVehicleType() throws Exception {
        mockMvc.perform(post("/vehicleType").content("{}".getBytes()))
                .andExpect(status().isOk());
        assertEquals("/vehicleType", capturedSubPath.get());
    }

    @Test
    void mapsDriverPhotoGet() throws Exception {
        mockMvc.perform(get("/driverPhoto"))
                .andExpect(status().isOk());
        assertEquals("/driverPhoto", capturedSubPath.get());
    }

    @Test
    void mapsSearchAddrByKeyword() throws Exception {
        mockMvc.perform(get("/searchAddrByKeyword"))
                .andExpect(status().isOk());
        assertEquals("/searchAddrByKeyword", capturedSubPath.get());
    }
}

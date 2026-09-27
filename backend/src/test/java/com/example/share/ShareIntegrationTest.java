package com.example.share;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
class ShareIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static final Path STORAGE = createStorage();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
        registry.add("app.storage.location", STORAGE::toString);
        registry.add("app.rate-limit.create-per-minute", () -> "1000");
        registry.add("app.rate-limit.verify-per-minute", () -> "1000");
        registry.add("app.share.cleanup-delay-ms", () -> "3600000");
        registry.add("app.share.max-file-size", () -> "1024");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsReadsAndRevokesATextShare() throws Exception {
        String body = mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TEXT","content":"Hello World","expirationMinutes":60,"password":"pw"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.managementToken").isString())
                .andExpect(jsonPath("$.passwordProtected").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = json(body, "token");
        String management = json(body, "managementToken");
        org.junit.jupiter.api.Assertions.assertNotEquals(token, management);

        mockMvc.perform(get("/api/shares/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordRequired").value(true))
                .andExpect(jsonPath("$.content").doesNotExist());

        mockMvc.perform(post("/api/shares/" + token + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));

        String accessBody = mockMvc.perform(post("/api/shares/" + token + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.share.content").value("Hello World"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String access = json(accessBody, "accessToken");

        mockMvc.perform(get("/api/shares/" + token).header("X-Share-Access", access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello World"));

        mockMvc.perform(delete("/api/shares/" + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/shares/" + token).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/shares/" + token).header("Authorization", "Bearer " + management))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/shares/" + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SHARE_REVOKED"));
    }

    @Test
    void storesAFileUnderAGeneratedKeyAndRejectsAnOversizedUpload() throws Exception {
        byte[] pdf = "%PDF-1.4\nhello\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "../../secret.txt", "image/png", pdf);

        String body = mockMvc.perform(multipart("/api/shares/file")
                        .file(file)
                        .param("expirationMinutes", "60"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not("")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = json(body, "token");
        mockMvc.perform(get("/api/shares/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("secret.txt"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        byte[] stored = Files.list(STORAGE)
                .filter(path -> !Files.isDirectory(path))
                .findFirst()
                .map(path -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, stored);
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(STORAGE.resolve("secret.txt")));

        byte[] big = new byte[2048];
        MockMultipartFile oversized = new MockMultipartFile("file", "big.bin", "application/octet-stream", big);
        mockMvc.perform(multipart("/api/shares/file")
                        .file(oversized)
                        .param("expirationMinutes", "60"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("all-share-it");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String json(String body, String field) {
        String needle = "\"" + field + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            throw new IllegalStateException("Missing " + field);
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}

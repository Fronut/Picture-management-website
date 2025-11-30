package com.imagemanagement.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.RefreshTokenRepository;
import com.imagemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
                refreshTokenRepository.deleteAll();
    }

    @Test
    void register_shouldCreateUserAndReturnToken() throws Exception {
        String payload = "{" +
                "\"username\":\"testuser\"," +
                "\"email\":\"test@example.com\"," +
                "\"password\":\"Password123\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("testuser"));

        assertThat(userRepository.existsByUsernameIgnoreCase("testuser")).isTrue();
    }

    @Test
    void login_shouldReturnTokenForExistingUser() throws Exception {
        User user = new User();
        user.setUsername("existinguser");
        user.setEmail("existing@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        userRepository.save(user);

        String payload = "{" +
                "\"usernameOrEmail\":\"existinguser\"," +
                "\"password\":\"Password123\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("existing@example.com"));
    }

    @Test
    void register_shouldFailWhenUsernameExists() throws Exception {
        User user = new User();
        user.setUsername("duplicate");
        user.setEmail("dup@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        userRepository.save(user);

        String payload = "{" +
                "\"username\":\"duplicate\"," +
                "\"email\":\"new@example.com\"," +
                "\"password\":\"Password123\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void refresh_shouldRotateRefreshTokens() throws Exception {
        String refreshToken = registerAndExtractRefreshToken("refreshuser", "refresh@example.com");

        String request = "{" +
                "\"refreshToken\":\"" + refreshToken + "\"}";

        var result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String rotatedToken = json.at("/data/refreshToken").asText();
        assertThat(rotatedToken).isNotBlank().isNotEqualTo(refreshToken);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void logout_shouldRevokeRefreshToken() throws Exception {
        String refreshToken = registerAndExtractRefreshToken("logoutuser", "logout@example.com");

        String request = "{" +
                "\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(request))
                .andExpect(status().isOk());

        assertThat(refreshTokenRepository.count()).isZero();
    }

    private String registerAndExtractRefreshToken(String username, String email) throws Exception {
        String payload = "{" +
                "\"username\":\"" + username + "\"," +
                "\"email\":\"" + email + "\"," +
                "\"password\":\"Password123\"}";

        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.at("/data/refreshToken").asText();
    }
}

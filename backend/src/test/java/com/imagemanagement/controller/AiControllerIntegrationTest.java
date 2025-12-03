package com.imagemanagement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.ai.AiServiceClient;
import com.imagemanagement.ai.dto.AiSearchInterpretation;
import com.imagemanagement.dto.request.AiSearchInterpretRequest;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.security.CustomUserDetails;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings({"null"})
class AiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AiServiceClient aiServiceClient;

    private User requester;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        requester = persistUser("ai-user", "ai-user@example.com");
    }

    @Test
    void interpretSearch_shouldReturnAiInterpretation() throws Exception {
        AiSearchInterpretation interpretation = new AiSearchInterpretation(
                "sunset",
                List.of("sunset"),
                List.of("tag:sunset"),
                Map.of("keyword", "sunset"),
                List.of(Map.of("rule", "sunset")),
                BigDecimal.valueOf(0.82));
        given(aiServiceClient.interpretSearch(anyString(), any())).willReturn(interpretation);

        AiSearchInterpretRequest request = new AiSearchInterpretRequest("Sunset", 5);

        mockMvc.perform(post("/api/ai/search/interpret")
                        .with(authentication(buildAuthentication(requester)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("sunset"))
                .andExpect(jsonPath("$.data.tags[0]").value("tag:sunset"));
    }

    @Test
    void interpretSearch_shouldValidateRequest() throws Exception {
        mockMvc.perform(post("/api/ai/search/interpret")
                        .with(authentication(buildAuthentication(requester)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new AiSearchInterpretRequest(" ", null))))
                .andExpect(status().isBadRequest());
    }

    private Authentication buildAuthentication(User user) {
        CustomUserDetails principal = new CustomUserDetails(userRepository.findById(user.getId()).orElseThrow());
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private User persistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }
}

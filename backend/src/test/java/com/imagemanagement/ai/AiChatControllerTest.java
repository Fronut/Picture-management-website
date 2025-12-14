package com.imagemanagement.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.ai.dto.AiChatSearchResult;
import com.imagemanagement.ai.dto.AiChatSearchResult.AiChatPrimaryResult;
import com.imagemanagement.ai.dto.AiChatSearchResult.AiToolCall;
import com.imagemanagement.ai.dto.AiChatSearchResult.AiToolFunction;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiServiceClient aiServiceClient;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(42L);
        user.setUsername("chat-user");
        user.setEmail("chat@example.com");
        user.setPasswordHash("secret");
        user.setStatus(UserStatus.ACTIVE);
        CustomUserDetails principal = new CustomUserDetails(user);
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void chat_shouldReturnAiResponseEnvelope() throws Exception {
        AiChatPrimaryResult primary = new AiChatPrimaryResult();
        primary.setSummary("matched images");
        primary.setRequestedLimit(3);

        AiToolFunction function = new AiToolFunction();
        function.setName("search_images");
        function.setArguments("{\"query\":\"sunset\"}");

        AiToolCall toolCall = new AiToolCall();
        toolCall.setId("call-1");
        toolCall.setType("function");
        toolCall.setFunction(function);

        AiChatSearchResult aiResponse = new AiChatSearchResult();
        aiResponse.setMessage("done");
        aiResponse.setPrimaryResult(primary);
        aiResponse.setResults(java.util.List.of(primary));
        aiResponse.setToolCalls(java.util.List.of(toolCall));

        given(aiServiceClient.chatSearch(any())).willReturn(aiResponse);

        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "query", "sunset beach",
                "limit", 3,
                "onlyOwn", true
        ));

        mockMvc.perform(post("/api/ai/chat")
                        .with(authentication(authentication))
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("done"))
                .andExpect(jsonPath("$.data.primaryResult.summary").value("matched images"))
                .andExpect(jsonPath("$.data.toolCalls", hasSize(1)))
                .andExpect(jsonPath("$.data.toolCalls[0].function.name").value("search_images"));
    }

    @Test
    void chat_shouldRejectBlankQuery() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("query", "   "));

        mockMvc.perform(post("/api/ai/chat")
                        .with(authentication(authentication))
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}

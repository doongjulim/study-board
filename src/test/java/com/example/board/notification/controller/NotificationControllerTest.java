package com.example.board.notification.controller;

import com.example.board.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService notificationService;

    @Test
    @DisplayName("GET /notifications/subscribe - SSE 스트림으로 응답한다")
    void subscribe() throws Exception {
        given(notificationService.subscribe()).willReturn(new SseEmitter());

        mockMvc.perform(get("/notifications/subscribe"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /notifications - 읽지 않은 개수와 최근 알림을 JSON 으로 반환한다")
    void list() throws Exception {
        given(notificationService.countUnread()).willReturn(3L);
        given(notificationService.findRecent()).willReturn(List.of());

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.notifications").isArray());
    }

    @Test
    @DisplayName("POST /notifications/read-all - 모두 읽음 처리한다")
    void readAll() throws Exception {
        mockMvc.perform(post("/notifications/read-all"))
                .andExpect(status().isOk());

        then(notificationService).should().markAllAsRead();
    }
}

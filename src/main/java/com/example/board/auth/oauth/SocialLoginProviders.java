package com.example.board.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * 실제로 설정된 소셜 로그인 제공자 목록.
 *
 * <p>로그인 화면이 "구글로 로그인" 을 그렸는데 자격증명이 없어 눌러도 오류가 나면,
 * 버튼이 없느니만 못하다. 그래서 화면은 <b>설정된 것만</b> 그린다.</p>
 *
 * <p>자격증명이 하나도 없으면 Spring Boot 는 ClientRegistrationRepository 빈을 만들지 않는다.
 * ObjectProvider 로 받는 이유가 그것이다 - 없어도 이 컴포넌트는 그대로 뜨고 빈 목록을 준다.</p>
 */
@Component
@RequiredArgsConstructor
public class SocialLoginProviders {

    private final ObjectProvider<ClientRegistrationRepository> repositoryProvider;

    public List<Provider> enabled() {
        ClientRegistrationRepository repository = repositoryProvider.getIfAvailable();
        if (!(repository instanceof InMemoryClientRegistrationRepository registrations)) {
            return List.of();
        }
        return StreamSupport.stream(registrations.spliterator(), false)
                .map(registration -> new Provider(
                        registration.getRegistrationId(), displayName(registration)))
                .toList();
    }

    public boolean isEnabled() {
        return !enabled().isEmpty();
    }

    /** 설정에 이름을 적어 두었으면 그것을, 아니면 등록 이름을 쓴다 */
    private String displayName(ClientRegistration registration) {
        String name = registration.getClientName();
        return (name == null || name.isBlank()) ? registration.getRegistrationId() : name;
    }

    /**
     * @param registrationId 인가 요청 주소에 들어가는 값 (/oauth2/authorization/{id})
     * @param displayName    버튼에 적을 이름
     */
    public record Provider(String registrationId, String displayName) {
    }
}

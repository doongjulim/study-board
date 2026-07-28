package com.example.board.member.service;

import com.example.board.auth.exception.LoginFailedException;
import com.example.board.member.domain.Member;
import com.example.board.member.dto.SignupForm;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signup(SignupForm form) {
        if (memberRepository.existsByLoginId(form.getLoginId())) {
            throw new DuplicateMemberException("loginId", "이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByNickname(form.getNickname())) {
            throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다.");
        }
        Member member = new Member(
                form.getLoginId(),
                passwordEncoder.encode(form.getPassword()),
                form.getNickname());
        return memberRepository.save(member).getId();
    }

    public Member authenticate(String loginId, String rawPassword) {
        return memberRepository.findByLoginId(loginId)
                .filter(member -> passwordEncoder.matches(rawPassword, member.getPassword()))
                .orElseThrow(LoginFailedException::new);
    }
}

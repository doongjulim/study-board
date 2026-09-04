-- 소셜 로그인.
--
-- 비밀번호 컬럼을 nullable 로 풀지 않는다. 소셜 계정에는 쓸 수 없는 값(!social)을 넣는다 -
-- 탈퇴 회원에게 !withdrawn 을 넣는 것과 같은 방식이다. BCrypt 해시가 아닌 값은 어떤 입력과도
-- 일치하지 않으므로, "비밀번호가 없다" 를 null 이라는 또 하나의 상태로 만들지 않아도 된다.
--
-- provider + provider_id 를 함께 유니크로 묶는다. 구글의 12345 와 카카오의 12345 는 다른 사람이다.
-- 두 컬럼이 모두 null 인 행(일반 가입)은 유니크 제약에 걸리지 않는다 - NULL 은 서로 다르게 취급된다.
alter table member add column oauth_provider varchar(20);
alter table member add column oauth_provider_id varchar(100);

create unique index uk_member_oauth on member (oauth_provider, oauth_provider_id);

-- 알림에 수신자(recipient) 도입: 전체 브로드캐스트 → 회원별 알림.
-- 기존 알림은 수신자를 특정할 수 없으므로 삭제한다 (로컬 개발 데이터만 존재).

delete from notification;

alter table notification add column recipient_id bigint not null;
alter table notification add constraint fk_notification_recipient foreign key (recipient_id) references member (id);

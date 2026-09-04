-- 프로필 이미지.
--
-- 게시글 첨부(attached_file)를 재사용하지 않는다. 그 엔티티는 글에 매달려 있어서
-- 회원에게 붙이려면 post_id 를 비워 둘 수 있게 풀어야 하고, 그러면 "첨부는 글에 속한다" 는
-- 규칙이 사라진다. 회원이 가진 값은 회원 행에 둔다.
--
-- content_type 을 함께 저장하는 이유: 업로드할 때 파일 내용(매직 넘버)으로 실제 형식을 확인하는데,
-- 그 결과를 버리면 내려 줄 때 확장자를 다시 믿어야 한다.
alter table member add column profile_image_stored_name varchar(100);
alter table member add column profile_image_content_type varchar(60);

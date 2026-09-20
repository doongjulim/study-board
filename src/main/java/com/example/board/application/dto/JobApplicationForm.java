package com.example.board.application.dto;

import com.example.board.application.domain.ApplicationResult;
import com.example.board.application.domain.ApplicationStage;
import com.example.board.application.domain.JobApplication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 지원 등록·수정 폼. 길이 상한은 엔티티가 정한다 (컬럼과 같은 값이어야 한다) */
@Getter
@Setter
public class JobApplicationForm {

    @NotBlank(message = "회사명을 입력하세요.")
    @Size(max = JobApplication.MAX_COMPANY_LENGTH, message = "회사명은 50자 이하로 입력하세요.")
    private String company;

    @Size(max = JobApplication.MAX_POSITION_LENGTH, message = "직무는 50자 이하로 입력하세요.")
    private String position;

    @NotNull(message = "전형 단계를 선택하세요.")
    private ApplicationStage stage = ApplicationStage.DOCUMENT;

    /** 등록 시에는 쓰지 않는다 - 방금 넣은 지원의 결과를 함께 묻는 것은 이상하다 */
    private ApplicationResult result = ApplicationResult.IN_PROGRESS;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;

    @Size(max = JobApplication.MAX_MEMO_LENGTH, message = "메모는 500자 이하로 입력하세요.")
    private String memo;

    public static JobApplicationForm from(JobApplication application) {
        JobApplicationForm form = new JobApplicationForm();
        form.setCompany(application.getCompany());
        form.setPosition(application.getPosition());
        form.setStage(application.getStage());
        form.setResult(application.getResult());
        form.setDeadline(application.getDeadline());
        form.setMemo(application.getMemo());
        return form;
    }
}

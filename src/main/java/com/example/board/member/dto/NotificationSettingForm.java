package com.example.board.member.dto;

import com.example.board.member.domain.NotificationPreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** 마이페이지의 알림 설정 */
@Getter
@Setter
public class NotificationSettingForm {

    private boolean reminderEnabled;

    @Min(value = 0, message = "알림 시점은 0분 이상이어야 합니다.")
    @Max(value = NotificationPreference.MAX_LEAD_MINUTES,
            message = "알림 시점은 60분 이하여야 합니다.")
    private int reminderLeadMinutes = NotificationPreference.DEFAULT_LEAD_MINUTES;

    private boolean planSharedEnabled;

    private boolean commentEnabled;

    public static NotificationSettingForm from(NotificationPreference preference) {
        NotificationSettingForm form = new NotificationSettingForm();
        form.setReminderEnabled(preference.isReminderEnabled());
        form.setReminderLeadMinutes(preference.getReminderLeadMinutes());
        form.setPlanSharedEnabled(preference.isPlanSharedEnabled());
        form.setCommentEnabled(preference.isCommentEnabled());
        return form;
    }
}

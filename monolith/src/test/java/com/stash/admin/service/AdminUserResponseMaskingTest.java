package com.stash.admin.service;

import com.stash.admin.api.dto.AdminUserDetailResponse;
import com.stash.admin.api.dto.AdminUserListItem;
import com.stash.platform.user.api.dto.AdminUserView;
import com.stash.shared.masking.GhanaCardMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Admin response masking — Ghana Card")
class AdminUserResponseMaskingTest {

    private static final UUID   USER_ID         = UUID.randomUUID();
    private static final String FULL_GHANA_CARD = "GHA-123456789-0";

    @Test
    @DisplayName("AdminUserListItem.maskedGhanaCard is the masked form, not the raw card")
    void listItemHasMaskedCard() {
        var item = toListItem(sampleViewWithMaskedCard());

        assertThat(item.maskedGhanaCard()).isEqualTo(GhanaCardMasker.maskToLastFour(FULL_GHANA_CARD));
        assertThat(item.maskedGhanaCard()).doesNotContain(FULL_GHANA_CARD);
    }

    @Test
    @DisplayName("AdminUserDetailResponse.user().maskedGhanaCard is the masked form")
    void detailHasMaskedCard() {
        var detail = new AdminUserDetailResponse(
                sampleViewWithMaskedCard(), List.of(), List.of(), List.of(), List.of());

        assertThat(detail.user().maskedGhanaCard()).isEqualTo(GhanaCardMasker.maskToLastFour(FULL_GHANA_CARD));
        assertThat(detail.user().maskedGhanaCard()).doesNotContain(FULL_GHANA_CARD);
    }

    @Test
    @DisplayName("masked card in list item starts with the fixed-length mask prefix")
    void maskedCardHasPrefix() {
        var item = toListItem(sampleViewWithMaskedCard());

        assertThat(item.maskedGhanaCard()).startsWith("••••••••");
    }

    @Test
    @DisplayName("null Ghana Card produces null maskedGhanaCard in list item")
    void nullCardProducesNullInListItem() {
        var view = new AdminUserView(USER_ID, "Test", "t@t.com", null,
                "PENDING", "ACTIVE", "FREE", Instant.now(), null);
        var item = toListItem(view);

        assertThat(item.maskedGhanaCard()).isNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AdminUserView sampleViewWithMaskedCard() {
        return new AdminUserView(
                USER_ID, "Test User", "test@stash.app", "+233241234567",
                "APPROVED", "ACTIVE", "FREE", Instant.now(),
                GhanaCardMasker.maskToLastFour(FULL_GHANA_CARD));
    }

    private AdminUserListItem toListItem(AdminUserView v) {
        return new AdminUserListItem(
                v.id(), v.displayName(), v.email(), v.phone(),
                v.kycStatus(), v.accountStatus(), v.subscriptionTier(),
                v.createdAt(), v.maskedGhanaCard());
    }
}

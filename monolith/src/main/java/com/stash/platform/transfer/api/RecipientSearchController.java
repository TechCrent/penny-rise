package com.stash.platform.transfer.api;

import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class RecipientSearchController {

    private final UserRepository userRepo;

    public RecipientSearchController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/search")
    public List<RecipientResult> search(
            @RequestParam("q") String query,
            @AuthenticationPrincipal UUID callerId) {

        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        return userRepo
                .searchEligibleRecipients(
                        query.trim(), callerId, KycStatus.APPROVED, PageRequest.of(0, 20))
                .stream()
                .map(u -> new RecipientResult(u.getId(), u.getDisplayName(), u.getEmail()))
                .toList();
    }

    public record RecipientResult(UUID id, String displayName, String email) {}
}

package com.stash.admin.api;

import com.stash.admin.api.dto.AdminUserDetailResponse;
import com.stash.admin.api.dto.AdminUserListResponse;
import com.stash.admin.rbac.AdminResource;
import com.stash.admin.rbac.RequiresAdminResource;
import com.stash.admin.service.AdminUserService;
import com.stash.platform.user.api.dto.AdminUserSearchCriteria;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @RequiresAdminResource(AdminResource.USER_MANAGEMENT)
    public AdminUserListResponse listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String kycStatus,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var criteria = new AdminUserSearchCriteria(search, kycStatus, accountStatus);
        return adminUserService.search(criteria, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @RequiresAdminResource(AdminResource.USER_MANAGEMENT)
    public AdminUserDetailResponse getUserDetail(@PathVariable UUID id) {
        return adminUserService.getDetail(id);
    }
}

package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.service.ServiceAccountService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/service-accounts")
public class AdminServiceAccountController {

    private final ServiceAccountService serviceAccountService;

    public AdminServiceAccountController(ServiceAccountService serviceAccountService) {
        this.serviceAccountService = serviceAccountService;
    }

    @GetMapping
    public List<ApiDtos.ServiceAccountResponse> listServiceAccounts() {
        return serviceAccountService.listServiceAccounts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ServiceAccountResponse createServiceAccount(
            @Valid @RequestBody ApiDtos.CreateServiceAccountRequest request,
            Principal principal
    ) {
        return serviceAccountService.createServiceAccount(request, principal.getName());
    }

    @PostMapping("/{serviceAccountId}/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ServiceAccountTokenResponse createToken(
            @PathVariable UUID serviceAccountId,
            @Valid @RequestBody ApiDtos.ServiceAccountTokenRequest request,
            Principal principal
    ) {
        return serviceAccountService.createToken(serviceAccountId, request, principal.getName());
    }

    @DeleteMapping("/{serviceAccountId}/tokens/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeToken(
            @PathVariable UUID serviceAccountId,
            @PathVariable UUID tokenId,
            Principal principal
    ) {
        serviceAccountService.revokeToken(serviceAccountId, tokenId, principal.getName());
    }
}

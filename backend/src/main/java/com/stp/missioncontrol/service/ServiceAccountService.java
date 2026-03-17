package com.stp.missioncontrol.service;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.ServiceAccount;
import com.stp.missioncontrol.model.ServiceAccountToken;
import com.stp.missioncontrol.repository.ServiceAccountRepository;
import com.stp.missioncontrol.repository.ServiceAccountTokenRepository;
import com.stp.missioncontrol.security.ServiceAccountPrincipal;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServiceAccountService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final ServiceAccountRepository serviceAccountRepository;
    private final ServiceAccountTokenRepository serviceAccountTokenRepository;
    private final AuditService auditService;

    public ServiceAccountService(
            ServiceAccountRepository serviceAccountRepository,
            ServiceAccountTokenRepository serviceAccountTokenRepository,
            AuditService auditService
    ) {
        this.serviceAccountRepository = serviceAccountRepository;
        this.serviceAccountTokenRepository = serviceAccountTokenRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ApiDtos.ServiceAccountResponse createServiceAccount(ApiDtos.CreateServiceAccountRequest request, String actor) {
        ServiceAccount account = new ServiceAccount(
                request.name(),
                request.description(),
                request.scopes(),
                request.allowedEnvironments(),
                request.allowedClusterIds() == null ? Set.of() : request.allowedClusterIds()
        );
        ServiceAccount saved = serviceAccountRepository.save(account);
        auditService.record(actor, "SERVICE_ACCOUNT_CREATED", "ServiceAccount", saved.getId().toString(), saved.getName());
        return toResponse(saved, false);
    }

    @Transactional
    public ApiDtos.ServiceAccountTokenResponse createToken(UUID serviceAccountId, ApiDtos.ServiceAccountTokenRequest request, String actor) {
        ServiceAccount account = serviceAccountRepository.findById(serviceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Service account not found"));
        String rawToken = generateRawToken();
        ServiceAccountToken token = new ServiceAccountToken(
                request.name(),
                rawToken.substring(0, Math.min(rawToken.length(), 12)),
                hashToken(rawToken),
                request.expiresAt()
        );
        account.addToken(token);
        serviceAccountRepository.save(account);
        auditService.record(actor, "SERVICE_ACCOUNT_TOKEN_CREATED", "ServiceAccount", serviceAccountId.toString(), request.name());
        return new ApiDtos.ServiceAccountTokenResponse(
                token.getId(),
                token.getName(),
                token.getTokenPrefix(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getLastUsedAt(),
                token.isRevoked(),
                rawToken
        );
    }

    @Transactional
    public void revokeToken(UUID serviceAccountId, UUID tokenId, String actor) {
        ServiceAccount account = serviceAccountRepository.findById(serviceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Service account not found"));
        ServiceAccountToken token = account.getTokens().stream()
                .filter(existing -> existing.getId().equals(tokenId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        token.revoke();
        serviceAccountRepository.save(account);
        auditService.record(actor, "SERVICE_ACCOUNT_TOKEN_REVOKED", "ServiceAccount", serviceAccountId.toString(), tokenId.toString());
    }

    @Transactional
    public ServiceAccountPrincipal authenticate(String rawToken) {
        String tokenHash = hashToken(rawToken);
        ServiceAccountToken token = serviceAccountTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Token is invalid"));

        if (token.isRevoked()) {
            throw new IllegalArgumentException("Token has been revoked");
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token has expired");
        }
        if (!token.getServiceAccount().isActive()) {
            throw new IllegalArgumentException("Service account is inactive");
        }

        token.markUsed();
        serviceAccountTokenRepository.save(token);
        auditService.record(token.getServiceAccount().getName(), "SERVICE_ACCOUNT_TOKEN_USED", "ServiceAccountToken", token.getId().toString(), "External API access");
        return new ServiceAccountPrincipal(
                token.getServiceAccount().getId(),
                token.getId(),
                token.getServiceAccount().getName(),
                token.getServiceAccount().getScopes(),
                token.getServiceAccount().getAllowedEnvironments(),
                token.getServiceAccount().getAllowedClusterIds()
        );
    }

    @Transactional
    public List<ApiDtos.ServiceAccountResponse> listServiceAccounts() {
        return serviceAccountRepository.findAll().stream()
                .map(account -> toResponse(account, false))
                .toList();
    }

    private ApiDtos.ServiceAccountResponse toResponse(ServiceAccount account, boolean includeRawToken) {
        return new ApiDtos.ServiceAccountResponse(
                account.getId(),
                account.getName(),
                account.getDescription(),
                account.isActive(),
                account.getCreatedAt(),
                account.getScopes(),
                account.getAllowedEnvironments(),
                account.getAllowedClusterIds(),
                account.getTokens().stream()
                        .map(token -> new ApiDtos.ServiceAccountTokenResponse(
                                token.getId(),
                                token.getName(),
                                token.getTokenPrefix(),
                                token.getCreatedAt(),
                                token.getExpiresAt(),
                                token.getLastUsedAt(),
                                token.isRevoked(),
                                includeRawToken ? token.getTokenPrefix() : null
                        ))
                        .toList()
        );
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "stpmc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

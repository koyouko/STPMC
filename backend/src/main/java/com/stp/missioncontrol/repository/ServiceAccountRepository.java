package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.ServiceAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceAccountRepository extends JpaRepository<ServiceAccount, UUID> {
}

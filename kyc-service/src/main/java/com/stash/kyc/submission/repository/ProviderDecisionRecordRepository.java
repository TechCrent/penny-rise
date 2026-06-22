package com.stash.kyc.submission.repository;

import com.stash.kyc.submission.domain.ProviderDecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ProviderDecisionRecordRepository extends JpaRepository<ProviderDecisionRecord, UUID> {

}
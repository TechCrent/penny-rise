package com.stash.kyc.document.repository;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycSubmissionDocumentRepository extends JpaRepository<KycSubmissionDocument, UUID> {

    List<KycSubmissionDocument> findBySubmissionId(UUID submissionId);

    @Query("""
            SELECT d FROM KycSubmissionDocument d
            WHERE d.submissionId = :submissionId AND d.documentType = :documentType
            """)
    Optional<KycSubmissionDocument> findBySubmissionIdAndDocumentType(
            @Param("submissionId") UUID submissionId,
            @Param("documentType") String documentType);
}

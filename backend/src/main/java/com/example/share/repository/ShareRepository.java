package com.example.share.repository;

import com.example.share.entity.Share;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareRepository extends JpaRepository<Share, UUID> {

    Optional<Share> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);

    @Query("select s.id from Share s where s.expiresAt <= :now")
    List<UUID> findExpiredIds(@Param("now") Instant now, Pageable pageable);
}

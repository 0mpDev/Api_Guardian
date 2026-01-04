package com.patniom.api_guardian.security.apikey;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {

    Optional<ApiKey> findByKeyValue(String keyValue);

    List<ApiKey> findByUserId(String userId);

    List<ApiKey> findByStatus(ApiKey.ApiKeyStatus status);

    List<ApiKey> findByTier(ApiKey.RateLimitTier tier);

    @Query("{ 'userId': ?0, 'deleted': false }")
    List<ApiKey> findActiveKeysByUserId(String userId);

    @Query("{ 'expiresAt': { $lt: ?0 }, 'status': 'ACTIVE' }")
    List<ApiKey> findExpiredKeys(LocalDateTime now);

    long countByUserIdAndDeletedFalse(String userId);

    boolean existsByKeyValue(String keyValue);
}
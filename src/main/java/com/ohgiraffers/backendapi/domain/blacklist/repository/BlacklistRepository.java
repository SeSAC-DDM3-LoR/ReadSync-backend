package com.ohgiraffers.backendapi.domain.blacklist.repository;

import com.ohgiraffers.backendapi.domain.blacklist.entity.Blacklist;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, Long> {

    @Query("SELECT b FROM Blacklist b WHERE b.user = :user AND b.isActive = true AND b.endDate > :now")
    Optional<Blacklist> findActiveBlacklistByUser(@Param("user") User user, @Param("now") LocalDateTime now);


    List<Blacklist> findByUserOrderByCreatedAtDesc(User user);

    
    @Query("SELECT b FROM Blacklist b WHERE b.isActive = true AND b.endDate > :now ORDER BY b.startDate DESC")
    List<Blacklist> findAllActiveBlacklists(@Param("now") LocalDateTime now);
}

package com.payment.payflow.repository;

import com.payment.payflow.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    // Our background poller will use this to fetch the oldest pending events first
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}

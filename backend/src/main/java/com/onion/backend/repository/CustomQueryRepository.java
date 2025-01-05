package com.onion.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class CustomQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void executeSleepQuery() {
        entityManager.createNativeQuery("SELECT SLEEP(2)").getResultList();
    }
}

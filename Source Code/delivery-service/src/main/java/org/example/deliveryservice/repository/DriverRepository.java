package org.example.deliveryservice.repository;

import org.example.deliveryservice.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findByAvailableTrue();
}

package com.traceback.traceback.repository;

import com.traceback.traceback.entity.ItemReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemReportRepository
        extends JpaRepository<ItemReport, Long> {
}
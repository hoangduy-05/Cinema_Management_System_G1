package com.fpt.cinema.repository;

import com.fpt.cinema.entity.AccountPermission;
import com.fpt.cinema.entity.AccountPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountPermissionRepository extends JpaRepository<AccountPermission, AccountPermissionId> {

    List<AccountPermission> findAllByAccountAccountId(Long accountId);
}

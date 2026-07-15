package com.fpt.cinema.repository;

import com.fpt.cinema.entity.AccountRole;
import com.fpt.cinema.entity.AccountRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {

    List<AccountRole> findAllByAccountAccountId(Long accountId);

    @Query("select ar.role.name from AccountRole ar where ar.account.accountId = :accountId")
    Set<String> findRoleNamesByAccountId(@Param("accountId") Long accountId);
}

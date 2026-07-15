package com.fpt.cinema.repository;

import com.fpt.cinema.entity.RolePermission;
import com.fpt.cinema.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findAllByRoleRoleId(Long roleId);
}

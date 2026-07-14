package com.fpt.cinema.security;

import com.fpt.cinema.entity.Account;
import com.fpt.cinema.repository.AccountRepository;
import com.fpt.cinema.repository.AccountRoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;

    public CustomUserDetailsService(
            AccountRepository accountRepository,
            AccountRoleRepository accountRoleRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        Account account = accountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));

        Set<String> roleNames = accountRoleRepository.findRoleNamesByAccountId(account.getAccountId());
        List<SimpleGrantedAuthority> authorities = roleNames.stream()
                .map(this::toAuthorityName)
                .map(SimpleGrantedAuthority::new)
                .toList();

        boolean active = ACTIVE_STATUS.equalsIgnoreCase(account.getStatus());
        return new User(
                account.getUsername(),
                account.getPasswordHash(),
                active,
                true,
                true,
                active,
                authorities
        );
    }

    private String toAuthorityName(String roleName) {
        return roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
    }
}

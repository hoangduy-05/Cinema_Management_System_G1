package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.LoginRequest;
import com.fpt.cinema.dto.request.RegisterRequest;
import com.fpt.cinema.dto.response.AuthResponse;
import com.fpt.cinema.entity.Account;
import com.fpt.cinema.entity.AccountRole;
import com.fpt.cinema.entity.AccountRoleId;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.Role;
import com.fpt.cinema.repository.AccountRepository;
import com.fpt.cinema.repository.AccountRoleRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.RoleRepository;
import com.fpt.cinema.security.JwtService;
import com.fpt.cinema.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthServiceImpl(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            RoleRepository roleRepository,
            AccountRoleRepository accountRoleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.roleRepository = roleRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        String phone = normalizeNullable(request.phone());

        requireUniqueAccountFields(username, email, phone);

        Role customerRole = roleRepository.findByNameIgnoreCase(CUSTOMER_ROLE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Role CUSTOMER chưa được cấu hình trong cơ sở dữ liệu"
                ));

        Account account = new Account();
        account.setUsername(username);
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setPhone(phone);
        account.setStatus(ACTIVE_STATUS);
        account.setCreatedAt(LocalDateTime.now(clock));
        account = accountRepository.save(account);

        Customer customer = new Customer();
        customer.setAccount(account);
        customer.setFullName(request.fullName().trim());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setAddress(normalizeNullable(request.address()));
        customerRepository.save(customer);

        AccountRoleId accountRoleId = new AccountRoleId();
        accountRoleId.setAccountId(account.getAccountId());
        accountRoleId.setRoleId(customerRole.getRoleId());

        AccountRole accountRole = new AccountRole();
        accountRole.setId(accountRoleId);
        accountRole.setAccount(account);
        accountRole.setRole(customerRole);
        accountRoleRepository.save(accountRole);

        return createAuthResponse(account, Set.of(customerRole.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();
        Account account = accountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(this::invalidCredentials);

        if (!ACTIVE_STATUS.equalsIgnoreCase(account.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản hiện không hoạt động"
            );
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw invalidCredentials();
        }

        Set<String> roles = accountRoleRepository.findRoleNamesByAccountId(account.getAccountId());
        return createAuthResponse(account, roles);
    }

    private void requireUniqueAccountFields(String username, String email, String phone) {
        if (accountRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại");
        }
        if (phone != null && accountRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã tồn tại");
        }
    }

    private AuthResponse createAuthResponse(Account account, Set<String> roles) {
        String token = jwtService.generateToken(account, roles);
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                account.getAccountId(),
                account.getUsername(),
                account.getEmail(),
                roles
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Tên đăng nhập, email hoặc mật khẩu không chính xác"
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

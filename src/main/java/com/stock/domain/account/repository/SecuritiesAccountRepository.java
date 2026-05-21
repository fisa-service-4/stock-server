package com.stock.domain.account.repository;

import com.stock.domain.account.entity.SecuritiesAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecuritiesAccountRepository extends JpaRepository<SecuritiesAccount, Long> {

  List<SecuritiesAccount> findByUserId(Long userId);

  Optional<SecuritiesAccount> findFirstByUserId(Long userId);
}

package com.stock.domain.account.repository;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SecuritiesAccountRepository extends JpaRepository<SecuritiesAccount, Long> {

  List<SecuritiesAccount> findByUserId(Long userId);

  Optional<SecuritiesAccount> findFirstByUserId(Long userId);

  List<SecuritiesAccount> findAllByAccountStatus(AccountStatus accountStatus);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SecuritiesAccount> findByAccountNumber(String accountNumber);

  Optional<SecuritiesAccount> findByBrokerCodeAndAccountNumber(
      String brokerCode, String accountNumber);

  @Query("SELECT a FROM SecuritiesAccount a WHERE a.cashBalance < 0")
  List<SecuritiesAccount> findAccountsWithNegativeCash();
}

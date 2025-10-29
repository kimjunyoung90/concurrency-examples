# Concurrency Examples

Spring Boot 3 & JPA 기반의 동시성 제어 전략 실습 프로젝트입니다.
재고 감소 로직에서 발생할 수 있는 **Race Condition** 문제를 재현하고,
이를 해결하기 위한 다양한 동시성 제어 방법을 구현합니다.

## 주요 기능

- 동시성 문제(Race Condition) 시뮬레이션
- **Synchronized** 키워드를 활용한 기본적인 동시성 제어
- **비관적 락(Pessimistic Lock)**: JPA `@Lock(PESSIMISTIC_WRITE)` 활용
- **낙관적 락(Optimistic Lock)**: JPA `@Version` 필드 기반 처리
- **Spring Retry**: Facade 패턴과 `@Retryable` 어노테이션을 활용한 재시도 전략
- 100개의 동시 요청을 처리하는 통합 테스트 (`ExecutorService` + `CountDownLatch`)

## 기술 스택

- Java 17
- Spring Boot 3.5.7
- Spring Data JPA
- MySQL 8
- JUnit 5
- Spring Retry
- Gradle

## 프로젝트 구조

```
src/
├── main/
│   ├── java/com/example/stock/
│   │   ├── StockApplication.java          # @EnableRetry 활성화
│   │   ├── domain/
│   │   │   └── Stock.java                 # JPA Entity with @Version
│   │   ├── repository/
│   │   │   └── StockRepository.java       # 비관적/낙관적 락 쿼리
│   │   ├── service/
│   │   │   └── StockService.java          # 동시성 제어 비즈니스 로직
│   │   └── facade/
│   │       └── OptimisticLockStockFacade.java  # 재시도 전용 계층
│   └── resources/
│       └── application.yaml               # MySQL 설정
├── test/
│   └── java/com/example/stock/service/
│       └── StockServiceTest.java          # 동시성 테스트
└── docs/
    └── concurrency_slides.marp.md         # 학습용 슬라이드
```

## 시작하기

### 데이터베이스 설정

MySQL 서버가 실행 중이어야 하며, 아래 설정으로 데이터베이스를 생성합니다:

```sql
CREATE DATABASE stock_example;
```

**접속 정보** (application.yaml):
- Host: `localhost:3306`
- Database: `stock_example`
- User: `root`
- Password: `1234`

### 실행 및 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew test --tests StockServiceTest.동시에_100개의_요청

# Spring Boot 애플리케이션 실행
./gradlew bootRun
```

## 동시성 제어 방법 비교

| 방법 | 구현 위치 | 장점 | 단점 |
|------|-----------|------|------|
| **Synchronized** | StockService:45 | 구현 간단 | 단일 서버에서만 작동, @Transactional과 함께 사용 불가 |
| **Pessimistic Lock** | StockService:54 | 강력한 데이터 일관성 | 성능 저하, 데드락 가능성 |
| **Optimistic Lock + Spring Retry** | StockService:62-68 | 높은 성능, 선언적 재시도 | 충돌 빈번 시 재시도 오버헤드 |

## 핵심 구현 코드

### 1. Stock Entity (낙관적 락)
```java
@Entity
public class Stock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private Long quantity;

    @Version  // 낙관적 락을 위한 버전 필드
    private Long version;

    public void decrease(Long quantity) {
        if(this.quantity - quantity < 0) {
            throw new RuntimeException("재고는 0개 미만이 될 수 없습니다.");
        }
        this.quantity -= quantity;
    }
}
```

### 2. Repository (비관적/낙관적 락 쿼리)
```java
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Stock findByIdWithPessimisticLock(Long id);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Stock s where s.id = :id")
    Stock findByIdWithOptimisticLock(Long id);
}
```

### 3. 재시도 계층 (Spring Retry)
```java
@Component
public class OptimisticLockStockFacade {

    private final StockService stockService;

    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = 50,
        backoff = @Backoff(delay = 50)
    )
    public void decrease(Long id, Long quantity) {
        stockService.decreaseWithOptimisticLock(id, quantity);
    }
}
```

Service는 비즈니스 로직만 담당:
```java
@Service
public class StockService {

    @Transactional
    void decreaseWithOptimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }
}
```

### 4. 동시성 테스트
```java
@Test
public void 동시에_100개의_요청() throws InterruptedException {
    int threadCount = 100;
    ExecutorService executorService = Executors.newFixedThreadPool(32);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                optimisticLockStockFacade.decrease(stockId, 1L);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();

    Stock stock = stockRepository.findById(stockId).orElseThrow();
    assertEquals(0, stock.getQuantity()); // 100 - 100 = 0
}
```

## 학습 포인트

### Spring Retry 적용 방법

#### Service에 직접 적용 (권장)

```java
@Service
public class StockService {

    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = 50,
        backoff = @Backoff(delay = 50)
    )
    @Transactional
    public void decreaseWithOptimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }
}
```

**주의**: Self-invocation 발생 시 `@Retryable` 작동 안 함

#### 접근 제어 계층 분리 (선택)

여러 Service를 조합하거나 접근 제어가 필요한 경우에만 사용

```java
@Component
public class OptimisticLockStockFacade {
    private final StockService stockService;

    public void decrease(Long id, Long quantity) {
        stockService.decreaseWithOptimisticLock(id, quantity);
    }
}

@Service
public class StockService {
    @Retryable(...)
    @Transactional
    void decreaseWithOptimisticLock(Long id, Long quantity) {
        // package-private으로 외부 직접 접근 차단
    }
}
```

**참고**: 계층 분리가 self-invocation 문제를 해결하는 것은 아님

### Synchronized의 한계

`synchronized` 키워드는 `@Transactional`과 함께 사용할 수 없습니다.

**이유**: Spring AOP는 트랜잭션을 프록시로 처리하므로 커밋 타이밍 문제가 발생합니다.

```
TransactionProxy (Spring AOP가 생성, 싱글톤) {
    startTransaction();              // 1. 트랜잭션 시작

    realService.synchronizedMethod();
    // 2. synchronized 메서드 실행
    // 3. synchronized 끝 -> 🔓 락 해제

    commitTransaction();             // 4. 커밋 (synchronized 밖에서!)
}
```

**문제의 타임라인**:
1. **Thread A**: synchronized 진입 (🔒)
2. **Thread A**: DB 작업 수행
3. **Thread A**: synchronized 종료 (🔓 락 해제)
4. **Thread B**: synchronized 진입 (🔒) - 이 시점에 Thread A는 **아직 커밋 전**
5. **Thread B**: DB 읽기 → 커밋되지 않은 옛날 데이터 읽음 (Race Condition!)
6. **Thread A**: 커밋 완료

**해결**: synchronized를 사용할 때는 @Transactional을 제거해야 함 (StockService:45-51 참고)

## 참고 자료

- [JPA Lock 공식 문서](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [Spring Retry 공식 문서](https://github.com/spring-projects/spring-retry)
- [동시성 제어 슬라이드](docs/concurrency_slides.marp.md)

이 프로젝트는 팀 세미나 발표, 블로그 포스팅, 기술 면접 준비 등에서 활용 가능한 **실전 중심 동시성 예제**입니다.
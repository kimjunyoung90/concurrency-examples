# Concurrency Examples

Spring Boot 3 & JPA 기반의 동시성 제어 전략 실습 프로젝트입니다.
재고 감소 로직에서 발생할 수 있는 **Race Condition** 문제를 재현하고,
이를 해결하기 위한 다양한 동시성 제어 방법을 구현합니다.

## 주요 기능

- 동시성 문제(Race Condition) 시뮬레이션
- **Synchronized** 키워드를 활용한 기본적인 동시성 제어
- **비관적 락(Pessimistic Lock)**: JPA `@Lock(PESSIMISTIC_WRITE)` 활용
- **낙관적 락(Optimistic Lock)**: JPA `@Version` + 서비스 계층 `@Retryable`로 낙관적 재시도 적용
- **MySQL Named Lock**: 네이티브 `get_lock/release_lock`으로 트랜잭션 분리 처리
- **Redis 기반 분산 락**: Lettuce 스핀 락과 Redisson `tryLock`을 이용한 분산 환경 대응
- 100개의 동시 요청을 처리하는 통합 테스트 (`ExecutorService` + `CountDownLatch`)

## 기술 스택

- Java 17
- Spring Boot 3.5.7
- Spring Data JPA
- Spring Data Redis
- Redisson
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
│   │   │   └── StockService.java          # 동시성 제어 비즈니스 로직 & @Retryable 적용
│   │   └── facade/
│   │       ├── OptimisticLockStockFacade.java  # 낙관적 락 서비스 진입점
│   │       ├── NamedLockStockFacade.java       # MySQL Named Lock 처리
│   │       ├── LettuceLockStockFacade.java     # RedisTemplate 기반 스핀 락
│   │       └── RedissonLockStockFacade.java    # Redisson tryLock 기반 분산 락
│   └── resources/
│       └── application.yaml               # MySQL 설정
├── test/
│   └── java/com/example/stock/
│       ├── service/StockServiceTest.java        # 서비스 계층 동시성 테스트
│       └── facade/*FacadeTest.java              # 각 락별 통합 테스트
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

# Redis 의존 기능 검증 (Lettuce/Redisson)
docker run --name redis-lock -p 6379:6379 -d redis:7-alpine
```

Redis 컨테이너를 사용하지 않는다면, 로컬 또는 클라우드 Redis 서버를 6379 포트에 띄운 뒤 RedisTemplate/Redisson 설정을 맞춰주세요.

## 추가 동시성 제어 전략

- **Named Lock (`NamedLockStockFacade`)**: MySQL 네이티브 락으로 재고 감소를 감싸고, `REQUIRES_NEW` 트랜잭션(`StockService.decreaseWithNewTransaction`)으로 커밋 타이밍을 분리합니다.
- **Lettuce 기반 Redis 락 (`LettuceLockStockFacade`)**: `RedisTemplate#setIfAbsent`와 3초 TTL로 스핀락을 구현하고, 획득 실패 시 100ms 대기 후 재시도합니다.
- **Redisson 분산 락 (`RedissonLockStockFacade`)**: `RLock.tryLock(10, 1, TimeUnit.SECONDS)`로 10초 이내 락을 기다리고, 실패 시 경고 로그를 남깁니다.

각 전략별로 `docs/` 폴더와 테스트(`src/test/java/com/example/stock/facade/*`)를 참고해 재현 시나리오를 확인할 수 있습니다.

## 동시성 제어 방법 비교

| 방법 | 구현 위치 | 장점 | 단점 |
|------|-----------|------|------|
| **Synchronized** | StockService:46 | 구현 간단 | 단일 JVM에서만 안전 |
| **Pessimistic Lock** | StockService:55 | 강력한 데이터 일관성 | 트랜잭션 지연, 데드락 위험 |
| **Optimistic Lock + Spring Retry** | StockService:63-74 | 높은 성능, 선언적 재시도 | 충돌 잦을 때 재시도 오버헤드 |
| **MySQL Named Lock** | NamedLockStockFacade:18-25 | DB 수준 전역 락, 레거시 환경 호환 | 락 남용 시 병목, 락 누수 주의 |
| **Redis Lettuce Spin Lock** | LettuceLockStockFacade:19-27 | 구현 단순, TTL로 락 해제 보장 | 스핀으로 인한 CPU 사용량 |
| **Redisson TryLock** | RedissonLockStockFacade:20-33 | 분산 환경 안정성, 자동 재진입 | Redis 인프라 필요, 외부 종속성 |

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

### 3. 서비스 계층 재시도 (Spring Retry)
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

### 4. MySQL Named Lock
```java
@Component
public class NamedLockStockFacade {

    @Transactional
    public void decrease(Long id, Long quantity) {
        try {
            lockRepository.getLock(id.toString());
            stockService.decreaseWithNewTransaction(id, quantity);
        } finally {
            lockRepository.releaseLock(id.toString());
        }
    }
}
```

### 5. Redis 분산 락
```java
@Component
public class LettuceLockStockFacade {

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (!redisLockRepository.lock(id)) {
            Thread.sleep(100);
        }
        try {
            stockService.decrease(id, quantity);
        } finally {
            redisLockRepository.unlock(id);
        }
    }
}
```

```java
@Component
public class RedissonLockStockFacade {

    public void decrease(Long id, Long quantity) {
        RLock lock = redissonClient.getLock(id.toString());
        try {
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);
            if (!available) {
                System.out.println("lock 획득 실패");
            }
            stockService.decrease(id, quantity);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
```

### 6. 동시성 테스트
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

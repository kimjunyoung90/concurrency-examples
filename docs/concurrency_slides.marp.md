---
marp: true
theme: default
class: invert
---

# 동시성 문제와 해결 전략
## Spring Boot & JPA 기반 실습

---

## 목차
1. 동시성 문제란?
2. 대표적 문제 유형: Race Condition
3. 해결 방법
   - Synchronized 키워드
   - 비관적 락(Pessimistic Lock)
   - 낙관적 락(Optimistic Lock)
4. Spring Retry를 활용한 재시도 전략
5. 주의사항: Self-Invocation과 계층 분리
6. 요약 및 비교
7. 실전 테스트 코드

---

## 1. 동시성 문제란?
- **정의**: 여러 프로세스나 스레드가 공유 자원에 동시에 접근할 때 발생하는 오류 상황
- **영향**: 데이터 무결성 손상, 예측 불가능한 결과, 시스템 불안정
- **중요성**: 최신 분산 시스템, 웹 서비스, 고부하 애플리케이션에서 필수적으로 처리해야 하는 문제

---

## 2. Race Condition
- **정의**: 두 개 이상의 작업이 동시에 실행될 때, 실행 순서에 따라 결과가 달라지거나 예기치 못한 오류가 발생하는 현상
- **특징**: 간헐적으로 발생하여 디버깅이 어려움
- **일반적인 발생 지점**: 데이터베이스 쓰기 작업, 공유 변수 수정, 파일 시스템 접근

---

## 3. Race Condition 예시 - Stock Entity

```java
@Entity
public class Stock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private Long quantity;

    public void decrease(Long quantity) {
        if(this.quantity - quantity < 0) {
            throw new RuntimeException("재고는 0개 미만이 될 수 없습니다.");
        }
        this.quantity -= quantity;
    }
}
```

---

## 4. Race Condition 예시 - Service

```java
@Service
public class StockService {

    @Transactional
    public void decrease(Long id, Long quantity) {
        // 1. Stock 조회
        Stock stock = stockRepository.findById(id).orElseThrow();

        // 2. 재고 감소
        stock.decrease(quantity);

        // 3. 저장
        stockRepository.saveAndFlush(stock);
    }
}
```

**문제점**: 다수의 스레드가 동시에 실행될 경우, 조회와 업데이트 사이의 시간차로 인해 동시성 문제 발생

---

## 5. Race Condition 테스트 코드

```java
@Test
public void 동시에_100개의_요청() throws InterruptedException {
    int threadCount = 100;
    ExecutorService executorService = Executors.newFixedThreadPool(32);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                stockService.decrease(stockId, 1L);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();  // 모든 스레드 종료 대기

    Stock stock = stockRepository.findById(stockId).orElseThrow();
    assertEquals(0, stock.getQuantity());  // 실패: 0보다 큰 값 발생
}
```

---

## 6. 해결 방법 1: Synchronized

```java
@Service
public class StockService {

    // @Transactional 없음 (synchronized와 함께 사용 불가)
    public synchronized void decreaseWithSynchronized(Long id, Long quantity) {
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }
}
```

**특징**:
- 간단한 구현
- 단일 서버 환경에서만 작동

**@Transactional과 함께 사용 불가 이유**:
```
TransactionProxy {
    startTransaction();
    synchronized메서드();    // synchronized 끝 -> 🔓 락 해제
    commitTransaction();    // 커밋은 synchronized 밖에서!
}
```
- Thread A가 synchronized를 벗어나면 락 해제되지만 아직 커밋 전
- Thread B가 진입해서 커밋되지 않은 데이터를 읽음 (race condition!)

---

## 7. 해결 방법 2: 비관적 락 (Pessimistic Lock)

- **개념**: 공유 자원에 대한 경합이 발생할 가능성이 높다고 가정하고 미리 잠금
- **작동 방식**: 트랜잭션이 시작될 때 즉시 데이터에 락을 설정
- **구현 방법**: JPA `@Lock` 어노테이션과 `SELECT ... FOR UPDATE` 사용
- **특징**: DBMS의 락 기능을 활용하여 강력한 동시성 제어

---

## 8. 비관적 락 구현 - Repository

```java
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Stock findByIdWithPessimisticLock(Long id);
}
```

**생성되는 SQL**:
```sql
SELECT * FROM stock WHERE id = ? FOR UPDATE;
```

---

## 9. 비관적 락 구현 - Service

```java
@Service
public class StockService {

    @Transactional
    public void decreaseWithPessimisticLock(Long id, Long quantity) {
        // 비관적 락으로 조회 (다른 트랜잭션은 대기)
        Stock stock = stockRepository.findByIdWithPessimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
}
```

**장점**: 데이터 일관성 보장, 구현 간단
**단점**: 성능 저하, 데드락 가능성

---

## 10. 해결 방법 3: 낙관적 락 (Optimistic Lock)

- **개념**: 충돌이 드물게 발생한다고 가정하고 버전 검사를 통해 충돌 감지
- **작동 방식**: 데이터 변경 시 버전 확인 후 업데이트, 버전 불일치 시 실패
- **구현 방법**: JPA `@Version` 어노테이션으로 버전 필드 추가
- **특징**: 락을 걸지 않아 성능 향상, 충돌 시 재시도 로직 필요

---

## 11. 낙관적 락 구현 - Entity

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

---

## 12. 낙관적 락 구현 - Repository

```java
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Stock s where s.id = :id")
    Stock findByIdWithOptimisticLock(Long id);
}
```

**생성되는 SQL (UPDATE 시)**:
```sql
UPDATE stock
SET quantity = ?, version = version + 1
WHERE id = ? AND version = ?;
```

버전이 일치하지 않으면 업데이트 실패 → `OptimisticLockException` 발생

---

## 13. 낙관적 락 구현 - Service

```java
@Service
public class StockService {

    @Transactional
    public void decreaseWithOptimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
        // 버전 불일치 시 ObjectOptimisticLockingFailureException 발생
    }
}
```

**문제점**: 충돌 발생 시 예외가 발생하므로 재시도 로직 필요

---

## 14. 낙관적 락 비관적 락 비교

|     | 비관적 락 | 낙관적 락 |
| --- | -------- | ----------- |
| **개념** | 충돌이 발생할 것이라고 가정 | 충돌이 적다고 가정 |
| **락 획득 시점** | 데이터 읽기 전 (SELECT) | 데이터 수정 시 (UPDATE) |
| **성능** | 동시성 높은 환경에서 성능 저하 | 충돌 적을 때 성능 우수 |
| **구현 복잡도** | 단순 (JPA @Lock) | 재시도 로직 필요 |
| **적합한 상황** | 경합이 자주 발생하는 환경<br>충돌 비용이 높은 경우 | 읽기가 많은 환경<br>충돌이 적은 경우 |
| **단점** | 데드락 가능성<br>성능 저하 | 충돌 시 재시도 필요<br>구현 복잡 |

---

## 15. Spring Retry 소개

**의존성 설정** (build.gradle):
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.springframework.retry:spring-retry'
}
```

**활성화** (StockApplication.java):
```java
@EnableRetry
@SpringBootApplication
public class StockApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockApplication.class, args);
    }
}
```

---

## 16. Spring Retry - Service에 직접 적용 (권장)

**일반적인 방법: Service에 직접 적용**
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

**장점**:
- 간단하고 직관적
- **재시도 로직은 비즈니스 로직의 일부이므로 Service에 있는 것이 자연스러움**
- 불필요한 계층 분리로 인한 복잡도 증가 없음

**주의**:
- AOP 순서: @Retryable이 @Transactional보다 먼저 적용되어야 함
- **Self-invocation**: Service 내부에서 자기 메서드 호출 시 @Retryable 작동 안 함

---

## 17. 주의사항: Self-Invocation 문제

- **문제점**: Spring AOP는 프록시 기반으로 동작하므로 내부 메서드 호출에는 AOP 적용 안됨

```java
@Service
public class MyService {
    @Transactional
    public void outer() {
        inner();  // ← self-invocation (this.inner()) → AOP 미적용
    }

    @Retryable
    @Transactional
    public void inner() {
        // 재시도와 트랜잭션 적용 안됨
    }
}
```

**결과**: `@Retryable`이나 `@Transactional`이 무시되어 예상대로 작동하지 않음

---

## 18. Self-Invocation 해결: 클래스 분리

**핵심 원리**: 다른 클래스의 메서드를 호출하면 Spring 프록시를 통해 AOP가 정상 작동

```java
@Service
public class OuterService {
    private final InnerService innerService;

    @Transactional
    public void outer() {
        innerService.inner();  // ✅ 프록시를 통한 호출
    }
}

@Service
public class InnerService {
    @Retryable
    @Transactional
    public void inner() {
        // ✅ AOP 정상 작동
    }
}
```

**하지만 문제점**: 어떤 클래스가 어떤 역할을 하는지 불명확

---

## 19. 선택사항: 접근 제어 계층 구현

### 접근 제어 계층이 유용한 경우
1. **여러 Service를 조합**해야 할 때
2. **접근 제어를 강제**하고 싶을 때 (Service를 package-private으로 제한하여 Facade를 통해서만 호출 가능)

### 역할 분리
```
┌─────────────────────┐
│   Controller        │
└──────────┬──────────┘
           │
           ↓
┌─────────────────────┐
│   Facade 계층        │  
└──────────┬──────────┘
           │
           ↓
┌─────────────────────┐
│   Service 계층       │  재시도 로직 (@Retryable), 비즈니스 로직 (@Transactional)
└─────────────────────┘
```

**트레이드오프**: 추가 계층 vs 명확한 분리

```java
@Component
public class OptimisticLockStockFacade {

    private final StockService stockService;
    
    public void decrease(Long id, Long quantity) {
        stockService.decreaseWithOptimisticLock(id, quantity);
    }
}
```

---

## 21. 접근 제한을 통한 안전성 확보

**Service (비즈니스 로직 + 트랜잭션)**:
```java
@Service
public class StockService {

    // package-private으로 제한 (외부 직접 접근 불가)
    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},  // 재시도할 예외
            maxAttempts = 50,                                           // 최대 시도 횟수
            backoff = @Backoff(delay = 50)                              // 재시도 간격 (ms)
    )
    @Transactional
    void decreaseWithOptimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }
}
```

**효과**:
- Controller나 다른 계층에서 Service를 직접 호출할 수 없음
- 반드시 Facade를 통해서만 호출하도록 강제
- 재시도 로직을 우회하는 실수 방지

**주의**: 이 패턴은 **접근 제어**를 위한 것이지, self-invocation 문제를 "해결"하는 것이 아님

---

## 22. Spring Retry 속성 설명

```java
@Retryable(
    retryFor = {ObjectOptimisticLockingFailureException.class},  // 재시도할 예외
    maxAttempts = 50,                                           // 최대 시도 횟수
    backoff = @Backoff(delay = 50)                              // 재시도 간격 (ms)
)
```

**고급 설정**:
```java
@Retryable(
    retryFor = {...},
    maxAttempts = 100,
    backoff = @Backoff(
        delay = 50,           // 초기 지연
        multiplier = 1.5,     // 지수 백오프 (50ms → 75ms → 112.5ms...)
        maxDelay = 1000       // 최대 지연 시간
    )
)
```

---

## 23. Spring Retry 동작 원리

```
1. optimisticLockStockFacade.decrease() 호출
   ↓
2. Spring AOP 프록시가 @Retryable 감지
   ↓
3. stockService.decreaseWithOptimisticLock() 실행
   ↓
4. ObjectOptimisticLockingFailureException 발생 시
   ↓
5. 50ms 대기 후 재시도 (최대 50번)
   ↓
6. 성공하면 종료, 50번 모두 실패하면 예외 throw
```

---

## 24. @Recover를 활용한 실패 처리

```java
@Component
public class OptimisticLockStockFacade {

    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = 50,
        backoff = @Backoff(delay = 50)
    )
    public void decrease(Long id, Long quantity) {
        stockService.decreaseWithOptimisticLock(id, quantity);
    }

    @Recover
    public void recover(ObjectOptimisticLockingFailureException e,
                        Long id, Long quantity) {
        log.error("재시도 실패: productId={}, quantity={}", id, quantity, e);
        // 알림, 로깅, 보상 트랜잭션 등
    }
}
```

---

## 25. 통합 테스트 코드

```java
@SpringBootTest
class StockServiceTest {

    @Autowired private OptimisticLockStockFacade optimisticLockStockFacade;
    @Autowired private StockRepository stockRepository;
    private Long stockId;

    @BeforeEach
    public void before() {
        Stock stock = stockRepository.saveAndFlush(new Stock(1L, 100L));
        stockId = stock.getId();
    }

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
        assertEquals(0, stock.getQuantity());  // ✅ 성공: 100 - 100 = 0
    }
}
```

---

## 26. 동시성 제어 방법 선택 가이드

| 상황 | 추천 방법 |
|------|----------|
| 단일 서버 + 간단한 로직 | Synchronized |
| 높은 충돌 가능성 | Pessimistic Lock |
| 낮은 충돌 가능성 | Optimistic Lock + Spring Retry |
| 읽기가 많은 환경 | Optimistic Lock |
| 다중 서버 환경 | 분산 락 (Redis, Zookeeper) |
| 성능이 중요한 경우 | Optimistic Lock |
| 데이터 일관성이 중요한 경우 | Pessimistic Lock |

---

## 27. 요약

- **Race Condition**: 동시 접근으로 인한 데이터 무결성 문제
- **Synchronized**: 간단하지만 단일 서버에서만 작동
- **Pessimistic Lock**: 강력한 일관성, 성능 저하 가능
- **Optimistic Lock**: 높은 성능, 재시도 로직 필요
- **Spring Retry**: 선언적 재시도, @Retryable 어노테이션으로 간편하게 구현
- **Self-Invocation**: 같은 클래스 내 메서드 호출 시 AOP 미적용
- **계층 분리**: 관심사 분리와 접근 제어를 통한 안전한 재시도 구현

---

## 28. 실전 프로젝트 구조

```
src/main/java/com/example/stock/
├── StockApplication.java          # @EnableRetry
├── domain/
│   └── Stock.java                 # @Version (낙관적 락)
├── repository/
│   └── StockRepository.java       # @Lock (비관적/낙관적 락 쿼리)
├── service/
│   └── StockService.java          # 비즈니스 로직 + @Transactional
└── facade/
    └── OptimisticLockStockFacade.java  # 재시도 전용 계층 (@Retryable)
```

**GitHub**: https://github.com/yourusername/concurrency-examples

---

## 29. 참고 자료

- **JPA Lock 공식 문서**
  - https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html

- **Spring Retry 공식 문서**
  - https://github.com/spring-projects/spring-retry

- **우아한형제들 기술블로그**
  - https://techblog.woowahan.com/2606/

- **테코블 - 동시성 관리**
  - https://tecoble.techcourse.co.kr/post/2023-08-16-concurrency-managing/

---

## 30. Q&A
질문이나 의견을 나눠주세요!

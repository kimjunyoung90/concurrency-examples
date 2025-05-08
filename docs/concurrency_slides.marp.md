---
marp: true
theme: default
class: invert
---

# 동시성 문제와 해결 전략
## 비관적 락과 낙관적 락 비교

---

## 목차
1. 동시성 문제란?
2. 대표적 문제 유형: Race Condition
3. 해결 방법
   - 비관적 락(Pessimistic Lock)
   - 낙관적 락(Optimistic Lock)
4. 재시도 전략
5. 주의사항
6. 요약 및 비교
7. 사례 연구

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

## 3. Race Condition 예시
```java
public void purchase(Long productId) {
    Product product = productMapper.selectProduct(productId);
    //SELECT * FROM product WHERE id = #{productId}

    if(product == null) return;

    if(product.getStock() <= 0) return;

    int result = productMapper.decreaseStock(productId);
    //UPDATE product SET stock = stock - 1 WHERE id = #{productId}
}
```
- **문제점**: 다수의 스레드가 동시에 실행될 경우, 재고 확인과 감소 사이의 시간차로 인해 동시성 문제 발생

---

## 4. Race Condition 테스트 코드
```java
private static final int THREAD_COUNT = 20;
private static final long PRODUCT_ID = 1L;

@Test
public void concurrentTest() {
    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

    for (int i = 0; i < THREAD_COUNT; i++) {
        executorService.submit(() -> {
            try {
                productService.purchase(PRODUCT_ID);
            } finally {
                latch.countDown();
            }
        });
    }

    //모든 스레드 종료 대기
    latch.await();

    //결과 조회
    Product product = productService.getProduct(PRODUCT_ID);
    System.out.println("최종 재고 수량: " + product.getStock());

    //재고는 0보다 작을 수 없다.
    assertTrue(product.getStock() >= 0);
}
```

---

## 5. 해결 방법: 비관적 락
- **개념**: 공유 자원에 대한 경합이 발생할 가능성이 높다고 가정하고 미리 잠금
- **작동 방식**: 트랜잭션이 시작될 때 즉시 데이터에 락을 설정
- **구현 방법**: `SELECT ... FOR UPDATE` 구문 사용
- **특징**: DBMS의 락 기능을 활용하여 보다 강력한 동시성 제어

---

## 6. 비관적 락 구현 예제
**SQL 쿼리**
```sql
BEGIN;

SELECT * FROM product WHERE product_id = 1 FOR UPDATE;

UPDATE product SET stock = stock - 1 WHERE product_id = 1;

COMMIT;
```

---
**Java 코드**
```java
public void purchasePessimistic(Long productId) {
    Product product = productMapper.selectProductForUpdate(productId);
    //SELECT * FROM product WHERE id = #{productId} FOR UPDATE;

    if(product == null) return;

    if(product.getStock() <= 0) return;

    productMapper.decreaseStock(productId);
    //UPDATE product SET stock = stock - 1 WHERE id = #{productId}
}
```

---

## 7. 해결 방법: 낙관적 락
- **개념**: 충돌이 드물게 발생한다고 가정하고 버전 검사를 통해 충돌 감지
- **작동 방식**: 데이터 변경 시 버전 확인 후 업데이트, 버전 불일치 시 실패
- **구현 방법**: 버전 컬럼을 추가하여 변경 시마다 버전 증가
- **특징**: 락을 걸지 않아 성능 향상, 충돌 시 재시도 로직 필요

---

## 8. 낙관적 락 구현 예제

**SQL 쿼리**
```sql
BEGIN;

SELECT * FROM product WHERE product_id = 1;

UPDATE product SET stock = stock - 1, version = version + 1 

WHERE product_id = 1 AND version = #{version};

COMMIT;
```

---

**Java 코드**
```java
public void purchaseOptimistic(Long productId) {

    Product product = productMapper.selectProduct(productId);
    
    if(product == null) return;

    if(product.getStock() <= 0) return;
    
    int result = productMapper.decreaseStockByVersion(productId, product.getVersion());

    
    if(result == 0) {
        // 버전 불일치로 업데이트 실패
        throw new OptimisticLockException("동시 수정 충돌 발생");
    }
}
```

---

## 9. 비관적 락 vs 낙관적 락

|     | 비관적 락 | 낙관적 락 |
| --- | -------- | ----------- |
| **개념** | 충돌이 발생할 것이라고 가정 | 충돌이 적다고 가정 |
| **락 획득 시점** | 데이터 읽기 전 | 데이터 수정 시 |
| **성능** | 동시성 높은 환경에서 성능 저하 | 충돌 적을 때 성능 우수 |
| **구현 복잡도** | 단순 | 재시도 로직 필요 |
| **적합한 상황** | 경합이 자주 발생하는 환경<br>충돌 비용이 높은 경우 | 읽기가 많은 환경<br>충돌이 적은 경우 |
| **단점** | 데드락 가능성<br>성능 저하 | 충돌 시 재시도 필요<br>구현 복잡 |

---

## 10. 낙관적 락 재시도 기본 구현
```java
private static final int RETRY_COUNTS = 3;

public void purchaseOptimisticWithRetry(Long productId) {
    int retryCount = 0;

    while(retryCount < RETRY_COUNTS) {
        Product product = productMapper.selectProduct(productId);
        
        if(product == null) return;

        if(product.getStock() <= 0) return;
        
        int result = productMapper.decreaseStockByVersion(productId, product.getVersion());
        
        if(result > 0) {
            break; // 성공 시 종료
        } else {
            retryCount++;
        }
    }
}
```

---

## 11. AOP를 활용한 재시도 처리

**어노테이션**
```java
@Target(ElementType.METHOD) //메서드에만 붙일 수 있음
@Retention(RetentionPolicy.RUNTIME) // 런타임
public @interface Retry {
    int maxAttempts() default 5;
    int retryDelay() default 100;
}
```
---
**재시도 구현 대상 메서드**
```java
@Retry(maxAttempts = 3, retryDelay = 100)
@Transactional
public void purchaseOptimisticWithAOP(Long productId) {

    Product product = productMapper.selectProduct(productId);
    
    if(product == null) return;

    if(product.getStock() <= 0) return;
    
    int result = productMapper.decreaseStockByVersion(productId, product.getVersion());

    if(result == 0) 
        throw new CustomOptimisticLockException("낙관적 락 충돌 발생");    
}
```

---

## 12. AOP 재시도 구현
```java
@Order(Ordered.LOWEST_PRECEDENCE - 1) //Aspect가 적용될 순서 지정(작은 숫자일수록 우선순위가 높고, 먼저 실행)
@Aspect
@Component
public class OptimisticLockRetryAspect {

    @Around("@annotation(retry)")
    public Object retryOptimisticLock(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        Exception exceptionHolder = null;
        for(int attempt = 0; attempt < retry.maxAttempts(); attempt++) {
            try {
                System.out.println("[재시도 로직] 시도 횟수: " + (attempt + 1));
                return joinPoint.proceed(); // 실제 메서드 실행, 에러없이 잘 수행되면 return 구문으로 재시도 없이 메서드 종료
            } catch (CustomOptimisticLockException e) {
                // 실패했을 때만 여기를 타고 재시도
                exceptionHolder = e;
                Thread.sleep(retry.retryDelay());
            }
        }
        // 위 루프를 다 돌고도 성공하지 못하면
        throw exceptionHolder;
    }
}
```

---

## 13. Spring-Retry 라이브러리 활용
```java
@Retryable(
    value = { CustomOptimisticLockException.class }, //재시도 케이스
    maxAttempts = 3, //재시도 횟수
    backoff = @Backoff(delay = 5000) //대기 시간
)
@Transactional
public void purchaseOptimisticWithRetry(Long productId) {

    Product product = productMapper.selectProduct(productId);
    
    if(product == null) return;

    if(product.getStock() <= 0) return;
    
    int result = productMapper.decreaseStockByVersion(productId, product.getVersion());

    if(result == 0) 
        throw new CustomOptimisticLockException("낙관적 락 충돌 발생");
}
```

---


## 14. 주의사항: Self-Invocation
- **문제점**: Spring AOP는 프록시 기반으로 동작하므로 내부 메서드 호출에는 AOP 적용 안됨
- **예시**:
```java
@Service
public class MyService {
    @Transactional
    public void outer() {
        inner(); // ← self-invocation (this.inner()) → AOP 미적용
    }

    @Retry
    @Transactional
    public void inner() {
        // 재시도와 트랜잭션 적용 안됨
    }
}
```
---
- **해결책**: 내부 메서드를 다른 서비스로 분리하여 프록시를 통해 호출하도록 함
```java
@Service
public class MyService {
    private final InnerService innerService;
    
    // 생성자 주입
    public MyService(InnerService innerService) {
        this.innerService = innerService;
    }
    
    @Transactional
    public void outer() {
        innerService.inner(); // 프록시를 통한 호출로 AOP 적용됨
    }
}

@Service
public class InnerService {
    @Retry
    @Transactional
    public void inner() {
        // 정상 동작
    }
}
```

---

## 15. 주의사항: 트랜잭션 전파 레벨
- **문제점**: 재시도 시 기본 전파 레벨(REQUIRED)에서는 예외 발생 시 트랜잭션이 rollback-only로 설정되어 UnexpectedRollbackException 발생 가능
- **해결책**: 낙관적 락 재시도 시 `REQUIRES_NEW` 전파 레벨 사용 권장
```java
@Retryable(...)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void purchaseOptimisticWithRetry(Long productId) {
    // 구현
}
```
---
**예시 코드**
```java
@Service
public class OuterServiceImpl implements OuterService {

    @Autowired
    private InnerService innerService;

    @Transactional
    public void outer() {
        System.out.println("OuterService > outer() 시작");
        try {
            innerService.inner();
        } catch (Exception e) {
            System.out.println("예외 발생");
        }

        System.out.println("OuterService > outer() 완료");
        // 여기서 아무 DB 작업이 없어도 커밋 시도
        // rollbackOnly 감지 → 예외 발생
    }
}
```
---
```java
@Service
public class InnerServiceImpl implements InnerService {
    int attempt = 0;

    @Transactional
    public void inner() {
        attempt++;
        System.out.println("InnerService > inner() : start");

        if (attempt == 1) throw new RuntimeException("예외 발생 - 재시도 필요");

        System.out.println("InnerService > inner() : end");
    }
}
```
---
<!-- slide: style="padding: 2em;" -->

![alt text align: center width:1000px height:550px ](mermaid-diagram-2025-05-06-010812-1.png)

---

## 16. 요약
- **동시성 문제**: 다중 스레드 환경에서 공유 자원 접근 시 발생하는 데이터 일관성 문제
- **비관적 락**: 데이터 접근 전 락 획득, 강력한 동시성 제어, 성능 저하 가능성
- **낙관적 락**: 버전 등을 통한 변경 감지, 성능 우수, 재시도 로직 필요
- **재시도 방식**: 반복문, AOP, spring-retry 라이브러리 활용 가능
- **주의사항**: self-invocation 문제, 트랜잭션 전파 레벨(REQUIRES_NEW 권장)

---

## 17. 결론
- **선택 기준**:
  - 높은 충돌 가능성 + 변경 비용 큼 = **비관적 락**
  - 낮은 충돌 가능성 + 읽기 작업 많음 = **낙관적 락**
- **효율적 설계**: 동시성 요구사항 명확히 정의하고, 적절한 전략 선택
- **테스트**: 실제 환경과 유사한 부하 테스트로 검증 필수
- **모니터링**: 실 환경에서 락 경합 및 성능 저하 모니터링

---

## 18. 사례 연구
- **우아한 테크코스**: 실제 현업에서 발생한 동시성 문제와 해결 방법
  - 재고 관리, 예약 시스템 등에서 동시성 제어 사례
  - https://tecoble.techcourse.co.kr/post/2023-08-16-concurrency-managing/

- **Spring Boot 사례**: Spring-retry와 @Retryable 활용 구현
  - 실제 운영 환경에서의 적용 사례와 효과
  - https://xxeol.tistory.com/57

- **UnexpectedRollbackException 해결**: 트랜잭션 전파 레벨 문제 해결
  - 실제 디버깅 과정과 해결책
  - https://techblog.woowahan.com/2606/

---

## 19. Q&A
질문이나 의견을 나눠주세요!


# Concurrency Examples

Java & Spring 기반의 동시성 제어 전략 실습 프로젝트입니다.  
재고 감소 로직에서 발생할 수 있는 **Race Condition** 문제를 재현하고,  
이를 해결하기 위한 **비관적 락(Pessimistic Lock)**과 **낙관적 락(Optimistic Lock)**을 구현합니다.

## 주요 기능

- 동시성 문제(Race Condition) 시뮬레이션
- 비관적 락 (`SELECT ... FOR UPDATE`) 기반 처리
- 낙관적 락 (버전 필드) 기반 처리 및 재시도 로직
    - 반복문 재시도
    - AOP 기반 재시도
    - Spring Retry 활용
- 트랜잭션 전파 레벨 및 Self-Invocation 문제 대응

## 기술 스택

- Java 17
- Spring Framework 5
- MyBatis
- MySQL
- JUnit 5
- Spring AOP / Spring Retry

## 프로젝트 구조
docs 에는 테스트 당시 사용했던 product 테이블 생성 ddl이 있습니다.
```
src/
├── main/
│ ├── java/com/myapp/
│ │ ├── config/ # 트랜잭션, AOP 설정
│ │ ├── product/ # 비즈니스 로직: 구매, 재고 감소
│ │ ├── annotation/ # @Retry 어노테이션
│ │ ├── aop/ # AOP 기반 재시도 로직
│ │ ├── exception/ # 사용자 정의 예외
│ └── resources/
│   ├── mapper/ # MyBatis 매퍼 XML
│   └── mybatis-config.xml
├── test/ # 동시성 테스트 코드
└── web/ # web.xml, dispatcher-servlet.xml
```

이 프로젝트는 팀 세미나 발표, 블로그 포스팅, 인터뷰 준비 등에서 활용 가능한 **실전 중심 동시성 예제**입니다.
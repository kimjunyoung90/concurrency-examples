# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot educational project demonstrating concurrency control patterns through a stock inventory management system. The project purposely includes race condition scenarios to explore various concurrency control solutions (e.g., synchronized, pessimistic/optimistic locking, Redis distributed locks).

**Key Architecture**:
- **Domain Model**: `Stock` entity with `decrease()` method that validates inventory constraints (quantity >= 0)
- **Service Layer**: `StockService` handles business logic for inventory reduction
- **Repository**: Standard Spring Data JPA repository pattern
- **Test Suite**: Includes concurrency test using `ExecutorService` and `CountDownLatch` to simulate 100 concurrent requests

**Concurrency Challenge**: The test `동시에_100개의_요청()` demonstrates race condition where 100 threads simultaneously decrease stock by 1, expecting final quantity of 0 from initial 100, but without proper locking, the result will be incorrect due to lost updates.

## Development Commands

**Build and Run Tests**:
```bash
./gradlew test                    # Run all tests
./gradlew test --tests StockServiceTest  # Run specific test class
./gradlew test --tests StockServiceTest.재고_감소  # Run specific test method
./gradlew clean build             # Clean build
./gradlew bootRun                 # Run Spring Boot application
```

**Database Setup**:
MySQL must be running on `localhost:3306` with:
- Database: `stock_example`
- User: `root`
- Password: `1234`

Create database: `CREATE DATABASE stock_example;`

The application uses `ddl-auto: create` which recreates tables on each run.

## Working with Concurrency Solutions

When adding new concurrency control mechanisms:

1. **Create New Service Implementation**: Add variant service classes (e.g., `StockServiceWithLock`, `StockServiceWithOptimistic`) rather than modifying the base `StockService`
2. **Add Corresponding Tests**: Each concurrency solution should have its own test class demonstrating the fix
3. **Repository Modifications**: For database-level locking (pessimistic/optimistic), add custom query methods to `StockRepository` with appropriate JPA annotations (`@Lock`, `@Query`)
4. **Transaction Management**: Pay attention to transaction boundaries - race conditions often occur due to transaction isolation levels

## Testing Patterns

The concurrency test pattern uses:
- `ExecutorService` with fixed thread pool (32 threads)
- `CountDownLatch` to synchronize thread completion
- `@BeforeEach`/`@AfterEach` to ensure clean test state with fresh Stock entity (productId=1, quantity=100)

When testing concurrency solutions, verify:
- Final stock quantity equals expected value (0 for 100 decrements)
- No exceptions thrown during concurrent execution
- Database state consistency after test completion

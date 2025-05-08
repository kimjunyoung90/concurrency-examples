package com.myapp.product.service;

import com.myapp.config.AppConfig;
import com.myapp.product.model.Product;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.util.concurrent.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AppConfig.class)
public class ProductServiceImplTest extends TestCase {

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    private static final int THREAD_COUNT = 10;
    private static final Long PRODUCT_ID = 1L;


    @Test
    public void 동시성_테스트() throws InterruptedException {
        //여러개의 스레드를 미리 생성해서 관리하는 객체
        //n개짜리 고정 스레드 풀을 만들고 submit하여 실행
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        //스레드들이 모두 끝날 때까지 테스트 코드가 기다리게 해주는 장치
        //각 스레드의 작업이 종료되면 latch.countDown()를 호출하여 횟수 감소
        //카운트가 0이 될때까지 latch.await()는 대기
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    productService.purchase(PRODUCT_ID);  // 내부에서 트랜잭션 + 재시도
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 작업 끝날 때까지 대기

        // 결과 조회
        Product product = productService.getProduct(PRODUCT_ID);
        System.out.println("최종 재고 수량: " + product.getStock());

        // 검증
        assertTrue(product.getStock() >= 0); // 재고가 음수가 되지 않아야 한다
    }

    @Test
    public void 재시도_테스트_1() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    productService.purchasePessimistic(PRODUCT_ID);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 작업 끝날 때까지 대기
        // 결과 조회
        Product product = productService.getProduct(PRODUCT_ID);
        System.out.println("최종 재고 수량: " + product.getStock());

        // 검증
        assertTrue(product.getStock() >= 0); // 재고가 음수가 되지 않아야 한다
    }

    @Test
    public void 재시도_테스트_2() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    productService.purchaseOptimisticWithAOP(PRODUCT_ID);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 작업 끝날 때까지 대기
        // 결과 조회
        Product product = productService.getProduct(PRODUCT_ID);
        System.out.println("최종 재고 수량: " + product.getStock());

        // 검증
        assertTrue(product.getStock() >= 0); // 재고가 음수가 되지 않아야 한다
    }

    @Test
    public void 재시도_테스트_3() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    productService.purchaseOptimisticWithRetrial(PRODUCT_ID);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 작업 끝날 때까지 대기
        // 결과 조회
        Product product = productService.getProduct(PRODUCT_ID);
        System.out.println("최종 재고 수량: " + product.getStock());

        // 검증
        assertTrue(product.getStock() >= 0); // 재고가 음수가 되지 않아야 한다
    }
}
package com.myapp.product.service;

import com.myapp.config.AppConfig;
import com.myapp.product.model.Product;
import com.myapp.test.service.OuterService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AppConfig.class)
public class UnexpectedRollbackTest {
    private static final int THREAD_COUNT = 10;
    private static final long PRODUCT_ID = 1L;

    @Autowired
    private OuterService outerService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Test
    public void testUnexpectedRollback() {
        outerService.outer();
    }

    @Test
    public void unexpectedRollbackWithOptimistic1() {
//        orderService.placeOrder(PRODUCT_ID);
        productService.purchaseOptimisticWithRetrial(PRODUCT_ID);
        // 결과 조회
        Product product = productService.getProduct(PRODUCT_ID);
        System.out.println("최종 재고 수량: " + product.getStock());

    }

    @Test
    public void unexpectedRollbackWithOptimistic2() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    orderService.placeOrder(PRODUCT_ID);
//                    productService.purchaseOptimisticAOP(PRODUCT_ID);
//                    productService.purchaseOptimisticWithRetrial(PRODUCT_ID);
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
        assertTrue(product.getStock() == 9); // 재고가 0이어야 한다.
    }
}

package com.myapp.product.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ProductService productService;

    @Transactional
    @Override
    public void placeOrder(Long productId) {

        System.out.println("주문 처리");

        try {
//            productService.purchase(productId);
//            productService.purchaseOptimisticWithAOP(productId);
            productService.purchaseOptimisticWithRetrial(productId);
        } catch (Exception e) {
            System.out.println("재고 차감 실패 처리 로직");
        }

        System.out.println("이후 처리...");
    }
}

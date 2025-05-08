package com.myapp.product.service;

import com.myapp.product.model.Product;

import java.sql.SQLException;

public interface ProductService {
    public Product getProduct(Long productId);
    public void purchase(Long productId) throws SQLException;
    public void purchasePessimistic(Long productId);
    public void purchaseOptimistic(Long productId);
    public void purchaseOptimisticRetry(Long productId);
    public void purchaseOptimisticWithAOP(Long productId);
    public void purchaseOptimisticWithRetrial(Long productId);
}

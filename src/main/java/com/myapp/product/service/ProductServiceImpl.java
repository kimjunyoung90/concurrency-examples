package com.myapp.product.service;

import com.myapp.product.annotation.Retry;
import com.myapp.product.exception.CustomOptimisticLockException;
import com.myapp.product.mapper.ProductMapper;
import com.myapp.product.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {
    private static final int RETRIAL_COUNTS = 3;
    @Autowired
    private ProductMapper productMapper;

    @Override
    public Product getProduct(Long productId) {
        return productMapper.selectProduct(productId);
    }

    @Override
    @Transactional
    public void purchase(Long productId) {
        System.out.println("재고 처리...");
        //재고 조회
        Product product = productMapper.selectProduct(productId);
        //SELECT * FROM product WHERE id = #{productId}
        if (product == null) {
            return;
        }

        if (product.getStock() <= 0) {
            return;
        }

        //재고 감소
        productMapper.decreaseStock(productId);
        //UPDATE product SET stock = stock - 1 WHERE id = #{productId}
    }

    @Override
    public void purchasePessimistic(Long productId) {
        //재고 조회
        Product product = productMapper.selectProductForUpdate(productId);
        //SELECT * FROM product WHERE id = #{productId} FOR UPDATE;
        if (product == null) {
            return;
        }

        if (product.getStock() <= 0) {
            return;
        }

        //재고 감소
        productMapper.decreaseStock(productId);
        //UPDATE product SET stock = stock -1 WHERE id = #{productId};
    }

    @Override
    public void purchaseOptimistic(Long productId) {
        //재고 조회
        Product product = productMapper.selectProduct(productId);
        //SELECT * FROM product WHERE id = #{productId};

        if (product == null) {
            return;
        }

        if (product.getStock() <= 0) {
            return;
        }

        //재고 감소
        productMapper.decreaseStockByVersion(productId, product.getStock());
        //UPDATE product SET stock = stock -1 WHERE id = #{productId} AND stock = #{prevStock};

    }

    /**
     * 재시도 예제(기본)
     * @param productId
     */
    public void purchaseOptimisticRetry(Long productId) {
        int attempts = 0;
        while (attempts < RETRIAL_COUNTS) {
            //재고 조회
            Product product = productMapper.selectProduct(productId);
            //SELECT * FROM product WHERE id = #{productId};

            if (product == null) {
                return;
            }

            if (product.getStock() <= 0) {
                return;
            }

            //재고 감소
            int result = productMapper.decreaseStockByVersion(productId, product.getStock());
            //UPDATE product SET stock = stock -1 WHERE id = #{productId} AND stock = #{prevStock};

            if(result == 0) {
                attempts++;
            } else {
                break;
            }
        }
    }

    /**
     * 재시도 예제(AOP)
     * @param productId
     */
    @Retry(maxAttempts = 3, retryDelay = 100)
    @Transactional
    public void purchaseOptimisticWithAOP(Long productId) {
        //재고 조회
        Product product = productMapper.selectProduct(productId);

        if (product == null) {
            return;
        }

        if (product.getStock() <= 0) {
            return;
        }

        //재고 감소
        int result = productMapper.decreaseStockByVersion(productId, product.getStock());
        if (result == 0) {
            throw new CustomOptimisticLockException("낙관적 락 충돌 발생");
        }
    }

    /**
     * 재시도 예제(Retryable)
     * @param productId
     */
    @Retryable(
            value = {CustomOptimisticLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purchaseOptimisticWithRetrial(Long productId) {
        //재고 조회
        Product product = productMapper.selectProduct(productId);

        if (product == null) {
            return;
        }

        if (product.getStock() <= 0) {
            return;
        }

        //재고 감소
        int result = productMapper.decreaseStockByVersion(productId, product.getStock());
        if (result == 0) {
            throw new CustomOptimisticLockException("낙관적 락 충돌 발생");
        }
    }
}

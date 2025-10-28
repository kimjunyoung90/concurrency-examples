package com.example.stock.facade;

import com.example.stock.service.StockService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class OptimisticLockStockFacade {

    private final StockService stockService;

    public OptimisticLockStockFacade(StockService stockService) {
        this.stockService = stockService;
    }

    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 50,
            backoff = @Backoff(delay = 50)
    )
    public void decrease(Long id, Long quantity) {
        stockService.decreaseWithOptimisticLock(id, quantity);
    }
}

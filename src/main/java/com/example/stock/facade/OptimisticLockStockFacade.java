package com.example.stock.facade;

import com.example.stock.service.StockService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class OptimisticLockStockFacade {

    private final StockService stockService;

    public OptimisticLockStockFacade(StockService stockService) {
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (true) {
            try {
                stockService.decreaseWithOptimisticLock(id, quantity);
                break;
            } catch (ObjectOptimisticLockingFailureException e) {
                Thread.sleep(50);
            }
        }
    }
}

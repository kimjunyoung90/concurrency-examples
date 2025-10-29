package com.example.stock.service;

import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void decrease(Long id, Long quantity) {
        // Stock 조회
        // 재고를 감소시킨 뒤
        // 갱신된 값 저장
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    /**
     * 1. synchronized
     * spring에서 @Transactional 어노테이션이 있으면 클래스를 매핑하는 클래스 생성
     * 각각의 새로운 클래스에서 아래처럼 Service의 메서드를 호출함.
     * <p>
     * 새로운 클래스 {
     * public void decrease(Long id, Long quantity) {
     * startTransaction();
     * stockService.decrease(id, quantity);
     * endTransaction();
     * }
     * }
     */
//    @Transactional
    public synchronized void decreaseWithSynchronized(Long id, Long quantity) {
        Stock stock = stockRepository.findById(id).orElseThrow();

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    @Transactional
    public void decreaseWithPessimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithPessimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 50,
            backoff = @Backoff(delay = 50)
    )
    @Transactional
    public void decreaseWithOptimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseWithNewTransaction(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
}

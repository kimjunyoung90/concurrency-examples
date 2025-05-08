package com.myapp.test.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InnerServiceImpl implements InnerService {
    int attempt = 0;

    /**
     * UnexpectedRollbackException 예외
     */
    @Transactional
    public void inner() {
        attempt++;
        System.out.println("InnerService > inner() : start");

        if (attempt == 1) throw new RuntimeException("예외 발생 - 재시도 필요");

        System.out.println("InnerService > inner() : end");
    }
}

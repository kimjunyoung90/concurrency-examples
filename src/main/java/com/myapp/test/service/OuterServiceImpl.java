package com.myapp.test.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OuterServiceImpl implements OuterService {

    @Autowired
    private InnerService innerService;

    @Transactional
    public void outer() {
        System.out.println("OuterService > outer() 시작");
        try {
            innerService.inner();
        } catch (Exception e) {
            System.out.println("예외 발생");
        }

        System.out.println("OuterService > outer() 완료");
        // 여기서 아무 DB 작업이 없어도 커밋 시도
        // rollbackOnly 감지 → 예외 발생
    }
}

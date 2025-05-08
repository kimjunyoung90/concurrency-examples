package com.myapp.product.mapper;

import com.myapp.product.model.Product;
import org.apache.ibatis.annotations.Param;

public interface ProductMapper {
    public Product selectProduct(long id);
    public Product selectProductForUpdate(long id);
    public void insertOrderHistory(long productId);
    public int decreaseStock(long id);
    public int decreaseStockByVersion(@Param("id") long id, @Param("version") long version);
}

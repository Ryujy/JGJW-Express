package com.zgzg.order.application.dto.res;

import java.math.BigDecimal;
import java.util.UUID;

import com.zgzg.order.domain.entity.Order;
import com.zgzg.order.domain.entity.OrderDetail;

import lombok.Getter;

@Getter
public class OrderDetailDTO {

	private UUID orderDetailId;
	private UUID productId;
	private String productName;
	private Integer quantity;
	private BigDecimal productPrice;

	public OrderDetail toEntity(Order order) {
		return OrderDetail.builder()
			.order(order)
			.productId(productId)
			.productName(productName)
			.quantity(quantity)
			.productPrice(productPrice)
			.build();
	}

	public OrderDetail convertEntity() {
		return OrderDetail.builder()
			.productId(this.getProductId())
			.productName(this.getProductName())
			.quantity(this.getQuantity())
			.productPrice(this.getProductPrice())
			.build();
	}
}

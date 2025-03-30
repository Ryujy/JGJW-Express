package com.zgzg.order.presentation.dto.req;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.zgzg.order.application.dto.res.OrderDetailDTO;
import com.zgzg.order.domain.entity.Order;
import com.zgzg.order.domain.entity.OrderDetail;
import com.zgzg.order.domain.entity.OrderStatus;

import lombok.Getter;

@Getter
public class CreateOrderRequestDto {

	private UUID supplierCompanyId;
	private UUID receiverCompanyId;
	private String supplierCompanyName;
	private String receiverCompanyName;
	private BigDecimal orderTotalPrice;
	private OrderStatus orderStatus;
	private String orderRequest;
	private String slackId;
	private List<OrderDetailDTO> productList;

	public Order toEntity(CreateOrderRequestDto requestDto, UUID supplierHubId) {
		return Order.builder()
			.supplierCompanyId(requestDto.getSupplierCompanyId())
			.receiverCompanyId(requestDto.getReceiverCompanyId())
			.supplierCompanyName(requestDto.getSupplierCompanyName())
			.receiverCompanyName(requestDto.getReceiverCompanyName())
			.supplierHubId(supplierHubId)
			.orderTotalPrice(requestDto.getOrderTotalPrice())
			.orderStatus(requestDto.getOrderStatus())
			.orderRequest(requestDto.getOrderRequest())
			.slackId(requestDto.getSlackId())
			.orderDetails(this.toDetailEntity())
			.build();
	}

	public List<OrderDetail> toDetailEntity(){
		List<OrderDetail> details = new ArrayList<>();
		for (OrderDetailDTO dto : this.productList) {
			OrderDetail entity = dto.convertEntity();
			details.add(entity);
		}
		return details;
	}
}

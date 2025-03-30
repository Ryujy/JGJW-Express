package com.zgzg.order.infrastructure.client;

import static com.zgzg.common.response.Code.*;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.zgzg.common.exception.BaseException;
import com.zgzg.common.response.ApiResponseData;
import com.zgzg.common.response.Code;
import com.zgzg.order.application.client.DeliveryClient;
import com.zgzg.order.application.client.ProductClient;
import com.zgzg.order.application.dto.req.CreateDeliveryRequestDTO;
import com.zgzg.order.application.service.OrderService;
import com.zgzg.order.domain.entity.Order;
import com.zgzg.order.domain.repo.OrderRepository;
import com.zgzg.order.infrastructure.dto.DeliveryResponseDTO;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryClientImpl implements DeliveryClient {

	private final FeignDeliveryClient feignDeliveryClient;
	private final ProductClient productClient;
	private final OrderRepository orderRepository;

	@Override
	public DeliveryResponseDTO getDelivery(UUID deliveryId) {
		return feignDeliveryClient.getDelivery(deliveryId);
	}

	@Override
	public UUID createDelivery(CreateDeliveryRequestDTO requestDTO) {
		log.info("Feign client : createDelivery");
		UUID deliveryId = null;
		// 분산 트랜잭션 적용
		try{
			ApiResponseData<UUID> response = feignDeliveryClient.createDelivery(requestDTO);
			deliveryId = response.getData();
		} catch (FeignException e){
			log.error("Error : createDelivery", e);
			rollbackOrder(requestDTO.getOrderId());
		}
		return deliveryId;
	}

	@Override
	public boolean cancelDelivery(UUID deliveryId) {
		ApiResponseData<DeliveryResponseDTO> response = feignDeliveryClient.cancelDelivery(deliveryId);
		if (response.getCode() != 3002) {
			return false;
		}
		return true;
	}

	private void rollbackOrder(UUID orderId) {
		// 재고 차감 -> 주문 생성 -> 배송 생성
		// 배송 생성 오류 -> 주문 취소로 상태 변경, 재고 원복
		Order order = orderRepository.findByIdAndNotDeleted(orderId);
		if (order == null) {
			throw new BaseException(ORDER_CANCEL_FAIL);
		} else {
			order.cancelOrder();
			log.info("order rollback success");
			productClient.increaseProduct(order.getOrderDetails());
			log.info("product rollback success");
		}
	}
}

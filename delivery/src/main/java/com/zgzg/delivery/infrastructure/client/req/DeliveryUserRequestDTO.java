package com.zgzg.delivery.infrastructure.client.req;

import java.util.UUID;

import com.zgzg.delivery.infrastructure.client.res.DeliveryStatus;
import com.zgzg.delivery.infrastructure.client.res.DeliveryType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUserRequestDTO {

	private UUID hubId;
	private DeliveryType deliveryType;
	private DeliveryStatus deliveryStatus;

	public static DeliveryUserRequestDTO completeDelivery(UUID hubId) {
		return DeliveryUserRequestDTO.builder()
			.hubId(hubId)
			.deliveryStatus(DeliveryStatus.CAN_DELIVER)
			.deliveryType(DeliveryType.STORE_DELIVERY)
			.build();
	}
}

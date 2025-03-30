package com.zgzg.order.infrastructure.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.zgzg.common.response.ApiResponseData;
import com.zgzg.order.application.client.ProductClient;
import com.zgzg.order.application.dto.res.OrderDetailDTO;
import com.zgzg.order.domain.entity.OrderDetail;
import com.zgzg.order.infrastructure.client.req.ProductStockRequestDTO;
import com.zgzg.order.infrastructure.client.res.ProductResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductClientImpl implements ProductClient {
	private final FeignProductClient feignProductClient;

	@Override
	public boolean getProduct(List<OrderDetailDTO> productList) {
		for (OrderDetailDTO dto : productList) {
			ApiResponseData<ProductResponseDTO> response = feignProductClient.getProduct(dto.getProductId());
			if (response.getData().getProductStock() - dto.getQuantity() < 0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean reduceProduct(List<OrderDetailDTO> productList) {

		for (OrderDetailDTO dto : productList) {
			ProductStockRequestDTO requestDTO = new ProductStockRequestDTO(dto.getProductId(), dto.getQuantity());
			ApiResponseData<String> response = feignProductClient.reduceProduct(requestDTO);
			if (response.getCode() != 6020) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean increaseProduct(List<OrderDetail> productList) {

		for (OrderDetail detail : productList) {
			ProductStockRequestDTO requestDTO = new ProductStockRequestDTO(detail.getProductId(), detail.getQuantity());
			ApiResponseData<String> response = feignProductClient.increaseProduct(requestDTO);
			log.info("product increase : {}, {}", requestDTO.getProductStock(),response.getMessage());
			if (response.getCode() != 6020) {
				return false;
			}
		}
		return true;
	}
}

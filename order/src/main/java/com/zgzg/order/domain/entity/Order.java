package com.zgzg.order.domain.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.zgzg.common.utils.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
public class Order extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID orderId;

	@Column(nullable = false)
	private UUID receiverCompanyId;

	@Column(nullable = false)
	private String receiverCompanyName;

	@Column(nullable = false)
	private UUID supplierHubId;

	@Column(nullable = false)
	private UUID supplierCompanyId;

	@Column(nullable = false)
	private String supplierCompanyName;

	@Column(nullable = false)
	private BigDecimal orderTotalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus orderStatus;

	@Column(columnDefinition = "TEXT")
	private String orderRequest;

	@Column(nullable = false)
	private String slackId;

	private UUID deliveryId;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@Builder.Default
	private List<OrderDetail> orderDetails = new ArrayList<>();

	public void cancelOrder() {
		this.orderStatus = OrderStatus.CANCELED;
	}

	public void addDeliveryOrder(UUID deliveryId) {
		this.deliveryId = deliveryId;
	}
}

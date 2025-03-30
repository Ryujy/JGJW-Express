package com.zgzg.delivery.application.service;

import static com.zgzg.common.response.Code.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zgzg.common.exception.BaseException;
import com.zgzg.common.security.CustomUserDetails;
import com.zgzg.delivery.application.client.DeliveryPersonClient;
import com.zgzg.delivery.application.client.HubClient;
import com.zgzg.delivery.application.client.SlackClient;
import com.zgzg.delivery.application.dto.res.DeliveryResponseDTO;
import com.zgzg.delivery.application.dto.res.DeliveryRouteLogsResponseDTO;
import com.zgzg.delivery.application.dto.res.DeliveryRouteResponseDTO;
import com.zgzg.delivery.application.dto.res.PageableResponse;
import com.zgzg.delivery.domain.entity.Delivery;
import com.zgzg.delivery.domain.entity.DeliveryRouteLog;
import com.zgzg.delivery.domain.entity.DeliveryStatus;
import com.zgzg.delivery.domain.repo.DeliveryRepository;
import com.zgzg.delivery.domain.repo.DeliveryRouteLogRepository;
import com.zgzg.delivery.infrastructure.client.req.DeliveryUserRequestDTO;
import com.zgzg.delivery.infrastructure.client.req.GenerateMessageRequest;
import com.zgzg.delivery.infrastructure.client.res.DeliveryUserResponseDTO;
import com.zgzg.delivery.infrastructure.dto.HubResponseDTO;
import com.zgzg.delivery.infrastructure.dto.RouteDTO;
import com.zgzg.delivery.presentation.dto.global.SearchCriteria;
import com.zgzg.delivery.presentation.dto.req.CreateDeliveryRequestDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

	private final DeliveryRepository deliveryRepository;
	private final DeliveryRouteLogRepository deliveryRouteLogRepository;
	private final HubClient hubClient;
	private final DeliveryPersonClient deliveryPersonClient;
	private final SlackClient slackClient;

	@Transactional
	public UUID createDelivery(CreateDeliveryRequestDTO requestDTO) {

		Delivery delivery = requestDTO.toEntity();
		Delivery savedDelivery = deliveryRepository.save(delivery);

		// 1. 배송 생성과 동시에 배송 담당자 할당
		DeliveryUserResponseDTO deliver = deliveryPersonClient.getHubDeiveryPerson();
		savedDelivery.assignDeliveryPerson(deliver.getDeliveryUserId(), deliver.getDeliverySlackUsername());
		// 2. 배송 생성과 동시에 배송 경로 요청
		List<RouteDTO> hubRoutes = hubClient.getHubRoutes(delivery.getOriginHubId(),
			delivery.getDestinationHubId());

		List<String> intermediateHubs = new ArrayList<>();

		try {
			// 배송 경로 저장 및 첫 번째 배송 경로에 배송 담당자 할당
			for (RouteDTO hubRoute : hubRoutes) {
				DeliveryRouteLog route = deliveryRouteLogRepository.save(hubRoute.toEntity(savedDelivery));

				if (hubRoute.getSequence() == 1) {
					route.assignDeliveryPerson(deliver.getDeliveryUserId(), deliver.getDeliverySlackUsername());
					savedDelivery.addOriginHubName(hubRoute.getStartHubName());
				} else if (hubRoute.getSequence() == hubRoutes.size() - 1) {
					savedDelivery.addDestinationHubName(hubRoute.getEndHubName());
				}
				if (hubRoute.getSequence() != 0 && hubRoute.getSequence() != hubRoutes.size() - 1) {
					intermediateHubs.add(hubRoute.getEndHubName());
				}
			}
		} catch (Exception e) {
			// 배송 경로 생성 실패 시 배송 담당자 할당 롤백
			DeliveryUserRequestDTO deliverRequestDTO = DeliveryUserRequestDTO.completeDelivery(deliver.getHubId());
			deliveryPersonClient.completeDeliveryPerson(deliver.getDeliveryUserId(), deliverRequestDTO);
			// 배송 상태 변경 : 배송 취소
			savedDelivery.cancelDelivery();
		}

		// 3. 배송 생성과 동시에 슬랙 알림 전송
		// // 슬랙 메시지 전송
		// GenerateMessageRequest messageRequest = GenerateMessageRequest.generate(requestDTO, savedDelivery,
		// 	intermediateHubs);
		// slackClient.createSlackMessage(messageRequest);

		return savedDelivery.getDeliveryId();
	}

	public DeliveryResponseDTO getDelivery(UUID deliveryId) {
		Delivery delivery = deliveryRepository.findByIdAndNotDeleted(deliveryId);
		return DeliveryResponseDTO.from(delivery);
	}

	@Transactional
	public void deleteDelivery(UUID deliveryId, CustomUserDetails userDetails) {
		Delivery delivery = deliveryRepository.findByIdAndNotDeleted(deliveryId);

		if (userDetails.getRole().equals("ROLE_HUB") && !hasHubAuth(delivery, userDetails)) {
			throw new BaseException(DELIVERY_AUTH_FORBIDDEN);
		}
		delivery.softDelete(userDetails.getUsername());
		deliveryRouteLogRepository.softDeleteRoutes(deliveryId);
	}

	@Transactional
	public DeliveryResponseDTO cancelDelivery(UUID deliveryId) {
		Delivery delivery = deliveryRepository.findByIdAndNotDeleted(deliveryId);
		if (!delivery.getDeliveryStatus().equals(DeliveryStatus.PREPARING)) {
			throw new BaseException(DELIVERY_CANCEL_FAIL);
		}
		delivery.cancelDelivery();
		return DeliveryResponseDTO.from(delivery);
	}

	public PageableResponse<DeliveryResponseDTO> searchOrder(SearchCriteria criteria, Pageable pageable) {
		Page<DeliveryResponseDTO> deliveryDTOPage = deliveryRepository.searchDeliveryByCriteria(criteria, pageable);
		return new PageableResponse<>(deliveryDTOPage);
	}

	public DeliveryRouteLogsResponseDTO getDeliveryRoutes(UUID deliveryId) {
		List<DeliveryRouteLog> routeLogs = deliveryRouteLogRepository.findByIdAndNotDeleted(deliveryId);
		List<DeliveryRouteResponseDTO> routeList = routeLogs.stream()
			.map(log -> DeliveryRouteResponseDTO.from(log))
			.toList();

		return new DeliveryRouteLogsResponseDTO(deliveryId, routeList);
	}

	@Transactional
	public void startDelivery(UUID deliveryId, int sequence) {
		DeliveryRouteLog route = deliveryRouteLogRepository.findByIdAndSequence(deliveryId, sequence);

		log.info("seq : {} , delivery id : {}", sequence, deliveryId);
		if (sequence == 1) {
			Delivery delivery = deliveryRepository.findByIdAndNotDeleted(deliveryId);
			if (delivery == null) {
				throw new BaseException(DELIVERY_NOT_FOUND);
			}

			delivery.startDelivery(); // 배송 상태 변경

			DeliveryUserResponseDTO deliver = deliveryPersonClient.getHubDeiveryPerson();
			route.assignDeliveryPerson(deliver.getDeliveryUserId(), deliver.getDeliverySlackUsername());
		}
		route.startDelivery(); // 배송 경로 상태 변경
	}

	@Transactional
	public void arriveDelivery(UUID deliveryId, int sequence) {
		// 허브 도착(담당자 할당, 실제 거리, 실제 소요 시간
		DeliveryRouteLog lastHub = deliveryRouteLogRepository.findByIdAndSequence(deliveryId, 0);
		DeliveryRouteLog currentRoute = deliveryRouteLogRepository.findByIdAndSequence(deliveryId, sequence);

		// 실제 소요 시간 계산
		long actualDuration = checkActualDuration(currentRoute);

		// todo. 슬랙 알림
		if (lastHub.getEndHubId().equals(currentRoute.getEndHubId())) { // 마지막 허브인 경우
			// 업체 배송 담당자 할당, "IN_DELIVERY"
			DeliveryUserResponseDTO deliver = deliveryPersonClient.getStoreDeiveryPerson(currentRoute.getEndHubId());
			currentRoute.assignDeliveryPerson(deliver.getDeliveryUserId(), deliver.getDeliverySlackUsername());
			currentRoute.startStoreDelivery(actualDuration);

			DeliveryRouteLog lastRoute = DeliveryRouteLog.addLastRoute(currentRoute, deliver);
			deliveryRouteLogRepository.save(lastRoute);

		} else {
			// 허브 배송 담당자 할당, "HUB_ARRIVED"
			DeliveryUserResponseDTO deliver = deliveryPersonClient.getHubDeiveryPerson();
			currentRoute.assignDeliveryPerson(deliver.getDeliveryUserId(), deliver.getDeliverySlackUsername());
			currentRoute.arrivedHub(actualDuration);
		}

	}

	@Transactional
	public void completeDelivery(UUID deliveryId, int sequence) {
		// 배송 완료 (요청 업체 수령 완료)
		Delivery delivery = deliveryRepository.findByIdAndNotDeleted(deliveryId);
		delivery.completeDelivery();
		log.info("deliver.completeDelivery");

		// 경로 기록
		DeliveryRouteLog route = deliveryRouteLogRepository.findByIdAndSequence(deliveryId, sequence);
		// 실제 소요 시간 계산
		long actualDuration = checkActualDuration(route);
		route.completeDelivery(actualDuration);
		// 실제 거리
		log.info("경로 기록");

		// 업체 배송 담당자 상태 변경
		DeliveryUserRequestDTO requestDTO = DeliveryUserRequestDTO.completeDelivery(route.getEndHubId());
		log.info("deliveryPersonClient");
		deliveryPersonClient.completeDeliveryPerson(route.getDeliveryPersonId(), requestDTO);
		log.info("deliveryPersonClient Complete");
	}

	private boolean hasHubAuth(Delivery delivery, CustomUserDetails userDetails) {
		HubResponseDTO response = hubClient.getHub(delivery.getOriginHubId());
		log.info("Hub Client : 허브 담당자 id - {}", response.getHubDTO().getHubAdminId());
		if (response.getHubDTO().getHubAdminId().equals(userDetails.getId())) {
			return true;
		}
		return false;

	}

	private long checkActualDuration(DeliveryRouteLog currentRoute) {
		Duration duration = Duration.between(LocalDateTime.now(), currentRoute.getModifiedDateTime());
		return duration.toMillis();
	}
}

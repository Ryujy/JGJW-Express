package com.zgzg.delivery.infrastructure.configuration;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;

import feign.RetryableException;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FeignClientRetryConfig {

	public Retryer retryer() {
		return new CustomRetryer();
	}

	public static class CustomRetryer implements Retryer {
		private final int maxAttempts;
		private final long backoff;
		private int attempt;

		public CustomRetryer() {
			this(100, 3);// 100ms씩 5번 시도
		}
		public CustomRetryer(long backoff, int maxAttempts) {
			this.backoff = backoff;
			this.maxAttempts = maxAttempts;
			this.attempt = 1;
		}

		@Override
		public void continueOrPropagate(RetryableException e) { //재시도 or 예외 전파
			if (attempt++ >= maxAttempts) { // 예외 전파
				log.error("Retry failed after {} attempts to {}", maxAttempts, e.request().httpMethod() + " " + e.request().url());
				throw e;
			}
			log.info("Retry attempt #{} after exception: {}, request: {} ", attempt, e.getMessage(), e.request().httpMethod() + " " + e.request().url());
			try {
				TimeUnit.MILLISECONDS.sleep(backoff); // 예외없이 종료되면 재시도 ㄱㄱ
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public Retryer clone() {
			return new CustomRetryer(backoff, maxAttempts);
		}
	}
}
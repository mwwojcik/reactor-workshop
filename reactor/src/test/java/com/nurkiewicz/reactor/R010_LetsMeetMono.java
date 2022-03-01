package com.nurkiewicz.reactor;

import com.nurkiewicz.reactor.samples.RestClient;
import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;


public class R010_LetsMeetMono {

	/**
	 * Tip: Avoid block() in production code
	 */
	@Test
	public void helloMono() throws Exception {
		//given
		//Mono.just to wydmuszka w api tylko wtedy gdy rezultat jest bardzo bardzo tani
		//Mono.just(restTemplate.get(url) - Mono zostanie stworzone gdy get się skończy
		//czyli jest BLOKUJĄCE

		final Mono<String> reactor = Mono.just("Reactor");

		//when
		//używaj tylko wtedy gdy testy lub połączeni z kodem blokującym
		final String value = reactor.block();

		//then
		assertThat(value).isEqualTo("Reactor");
	}

	@Test
	public void emptyMono() throws Exception {
		//given
		final Mono<String> reactor = Mono.empty();

		//when
		final String value = reactor.block();

		//then
		assertThat(value).isNull();
	}

	@Test
	public void errorMono() throws Exception {
		//given
		final Mono<String> error = Mono.error(new UnsupportedOperationException("Simulated"));

		//when
		try {
			error.block();
			failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
		} catch (UnsupportedOperationException e) {
			//then
			assertThat(e).hasMessage("Simulated");
		}
	}

	@Test
	public void monoIsEager() throws Exception {
		//given
		AtomicInteger counter = new AtomicInteger();

		//when
		Mono.just(counter.incrementAndGet());

		//then
		assertThat(counter).hasValue(1);
	}

	@Test
	public void monoIsLazy() throws Exception {
		//given
		AtomicInteger counter = new AtomicInteger();

		//when
		Mono.fromCallable(() -> counter.incrementAndGet());

		//then
		assertThat(counter).hasValue(0);
	}

	@Test
	public void lazyWithoutCaching() throws Exception {
		//given
		AtomicInteger counter = new AtomicInteger();
		final Mono<Integer> lazy = Mono.fromCallable(() -> counter.incrementAndGet());

		//when
		//pytamy się dwukrotnie i mono również odpali się dwukrotnie
		final Integer first = lazy.block();
		final Integer second = lazy.block();

		/*
		* trzeba uważać bo np. wysyłamy komuś maila więc jeśli dwa razy to zrobimy to możemy wysłać dwukrotnie maila
		* */
		//then
		assertThat(first).isEqualTo(1);
		assertThat(second).isEqualTo(2);
	}

	/**
	 * TODO: use {@link Mono#cache()} operator to call {@link AtomicInteger#incrementAndGet()} only once.
	 */
	@Test
	public void cachingMonoComputesOnlyOnce() throws Exception {
		//given
		AtomicInteger counter = new AtomicInteger();
		final Mono<Integer> lazy = Mono.fromCallable(counter::incrementAndGet).cache();

		//when
		//tu uruchamia się kod dla 1 osoby
		lazy.block();
		//ta osoba dostaje to co pierwsza
		lazy.block();

		//then
		assertThat(counter).hasValue(1);
	}

	/**
	 * TODO Use {@link Mono#cache()} to avoid calling destructive method twice
	 */
	@Test
	public void nonIdempotentWebService() throws Exception {
		//given
		RestClient restClient = new RestClient();
		final Mono<Object> result = Mono.fromRunnable(() -> restClient.post(1));

		//when
		result.block();
		result.block();

		//then
		//no exceptions
	}

}

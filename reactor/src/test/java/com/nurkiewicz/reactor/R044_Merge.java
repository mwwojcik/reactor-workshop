package com.nurkiewicz.reactor;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import com.nurkiewicz.reactor.samples.CacheServer;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static java.time.Duration.ofMillis;


public class R044_Merge {

	private static final Logger log = LoggerFactory.getLogger(R044_Merge.class);

	/**
	 * Subskrybuje się na każdym ze strumieni i emituje zdarzenia niezależnie. To odróżnia go od zip() (bo ten
	 * emituje parę)
	 */
	@Test
	public void mergeCombinesManyStreams() throws Exception {
		//given
		final Flux<String> fast = Flux.interval(ofMillis(90)).map(x -> "F-" + x);
		final Flux<String> slow = Flux.interval(ofMillis(100)).map(x -> "S-" + x);

		//when
		final Flux<String> merged = Flux.merge(
				fast,
				slow
		);

		//then
		merged.subscribe(log::info);
		TimeUnit.SECONDS.sleep(2);
	}

	@Test
	public void mergingMonos() throws Exception {
		//given
		final Mono<BigDecimal> fast = Mono
				.just(BigDecimal.valueOf(1))
				.delayElement(ofMillis(200));

		final Mono<BigDecimal> slow = Mono
				.just(BigDecimal.valueOf(2))
				.delayElement(ofMillis(100));

		//when
		final Flux<BigDecimal> merged = Flux.merge(
				fast,
				slow
		);

		//then
		merged.subscribe(d -> log.info("Received {}", d));
		TimeUnit.SECONDS.sleep(2);
	}

	private CacheServer first = new CacheServer("foo.com", ofMillis(20), 0);
	private CacheServer second = new CacheServer("bar.com", ofMillis(20), 0);

	/**
	 * TODO Fetch data from first available cache server.
	 *
	 * BTW this can also be achieved using {@link Mono#firstWithSignal(Mono[])}
	 * @see Flux#mergeWith(Publisher)
	 * @see Flux#next()
	 */
	@Test
	public void fetchDataFromFirstAvailableServer() throws Exception {
		//given
		final Mono<String> fv = first.findBy(42);
		final Mono<String> sv = second.findBy(42);

		//when
		//Mono<String> fastest = Mono.firstWithSignal(fv,sv);
		//jeśli któryś skończy się błędem to błąd też zostanie spropagowany, jest inna metoda first() która łyka błedy

		//alternatywa - zmerguj dwa sygnały i weź ten który jest pierwszy
		Mono<String> fastest=Flux.merge(fv,sv).next();

		//then
		fastest
				.doOnNext(log::info)
				.as(StepVerifier::create)
				.expectNextMatches(v -> v.startsWith("Value-42"))
				.verifyComplete();
	}

}

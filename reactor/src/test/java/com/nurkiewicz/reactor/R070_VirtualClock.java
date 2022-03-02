package com.nurkiewicz.reactor;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static reactor.test.StepVerifier.withVirtualTime;

/*
* Operatory związane z czasem np. timeout najczęściej przyjmują scheduler, któ®y mówi na jakim wątku ma pojawić się
* timeout, bo gdy mamy asynchroniczną operację to potrzebujemy innego wątku który obudzi ją np. wyjątkiem
*
*bardzo czesto domyślnym schedulerem jest Schedulers.parallel()
*
* Gdy tworzymy mone i opakujemy w metodę withVirtualTime (standardowa z pakietu testowego), to do każdego operatora
* wrzucamy virtualny scheduler pozwalający na wpływanie na czas. Dzięki temu można zrobić test czy np.
* aplikacja dobrze zachowuje się w czasie, np. robi coś raz na 2h . Test symuluje upływ czasu bez usypiania aplikacji.
* */
public class R070_VirtualClock {

	private static final Logger log = LoggerFactory.getLogger(R070_VirtualClock.class);

	@Test
	public void virtualTime() throws Exception {
		withVirtualTime(this::longRunning)
				.expectSubscription()
				.expectNoEvent(ofSeconds(2))
				.expectNext("OK")
				.expectComplete()
				.verify(ofSeconds(5));
	}

	/**
	 * TODO Apply {@link Mono#timeout(Duration)} of 1 second to a return value from {@link #longRunning()} method and verify it works.
	 * Warning: {@link reactor.test.StepVerifier.LastStep#verifyTimeout(Duration)} doesn't verify {@link java.util.concurrent.TimeoutException}
	 */
	@Test
	public void timeout() throws Exception {
		//TODO Write whole test :-)
		withVirtualTime(this::longRunning)
				.expectSubscription()
				//to jest czas wirtualny
				.expectNoEvent(Duration.ofMillis(500))
				.expectError(TimeoutException.class)
				//.expectTimeout(ofMillis(1000))
				//to jest czas rzeczywisty
				.verify(ofSeconds(5));
	}

	Mono<String> longRunning() {
		return Mono
				.delay(Duration.ofMillis(2000))
				.map(x -> "OK")
				.timeout(ofMillis(1000));
	}

}

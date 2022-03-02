package com.nurkiewicz.reactor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;


public class R051_WhatIsAscheduler {
/*
* Scheduler jest opakowaniem javowej puli wątków. W klasie Schedulers są metody fabrykujące. Najlepsze jest
* newBoundedElastic(ile wątków, ile zadań w kolejce może czekać, nazwa) - używać bardziej do IO
* newParallel - do zadań zablokowanych tylko na procesorze, kompresja, kryptografia, kodowanie multimediów, wątki w
* tej puli mają flagę która pozwala narzędziom diagnostycznym wykrycie że następiło zakleszczenie. Jest to normalne
* dla wątków BoundedElastic - IO bo mogą wisieć na sockecie. Jest to niedopuszcalne dla newParallel().
* Nigdy nie tworzyć puli wewnątrz subscribeOn - raczej jako bean springowy.
* Nie używać:
* boundedElastic() - nazwa myląca , nie jest elastic - nie używać bo jest zależna od listy corów, niemonitorowana
* parallel() - z nich korzysta reaktor - jak się zagłodzi to przestanie działać
* */
	private static final Logger log = LoggerFactory.getLogger(R051_WhatIsAscheduler.class);

	/**
	 * TODO Implement {@link #customScheduler()}
	 */
	@Test
	public void createCustomScheduler() throws Exception {
		//given
		AtomicReference<String> seenThread = new AtomicReference<>();
		final Mono<Void> mono = Mono.fromRunnable(() -> {
			seenThread.set(Thread.currentThread().getName());
		});

		//when
		mono
				.subscribeOn(customScheduler())
				.block();

		//then
		assertThat(seenThread.get()).matches("Custom-\\d+");
	}

	/**
	 * TODO Implement custom bound scheduler.
	 * It must contain 10 threads named "Custom-" and a sequence number.
	 * @see Executors#newFixedThreadPool(int)
	 * @see ExecutorService
	 * @see ThreadFactoryBuilder
	 * @see Schedulers#fromExecutorService(ExecutorService)
	 */
	private Scheduler customScheduler() {
		return Schedulers.boundedElastic();
	}

}

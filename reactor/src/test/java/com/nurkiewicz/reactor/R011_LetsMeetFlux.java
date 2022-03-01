package com.nurkiewicz.reactor;

import org.junit.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;


public class R011_LetsMeetFlux {

    @Test
    public void helloFlux() throws Exception {
        //given
        //wołanie gorliwe, gdy woła się just to już jest pozamiatane,
        //Mono.just(havyDBMethod()) - zanim stworzy się zwinny Mono
        //to havyDBMethod() już się wywoła - nie ma żadnego zysku
        final Flux<String> reactor = Flux.just("Reactor");

        //when
        final List<String> value = reactor.collectList().block();

        //then
        assertThat(value).containsExactly("Reactor");
    }
    /*
     * Mono.just może się przydać gdy metoda jest bardzo tania, nie opłaca się callable()
     * if(cache.contains("key")){
     * 	return Mono.just(cache.get("key"))
     * }else{
     * 	return Mono.fromCallable(()->getFromDb()}
     * */

    @Test
    public void emptyFlux() throws Exception {
        //given
        final Flux<String> reactor = Flux.empty();

        //when
        final List<String> value = reactor.collectList().block();

        //then
        assertThat(value).isEmpty();
    }

    @Test
    public void manyValues() throws Exception {
        //given
        final Flux<String> reactor = Flux.just("Reactor", "library");

        //when
        //collectList() - Może skończyć się pamięć
        //musi czekać do zakończenia strumienia, gdy strumień jest nieskończony to ten operator nic nie wyemituje
        //jeden wyjątek powoduje że nie dostaniemy żadnego elementu z całej listy
        final List<String> value = reactor.collectList().block();

        //then
        assertThat(value).containsExactly("Reactor", "library");
    }

    @Test
    public void errorFlux() throws Exception {
        //given
        final Flux<String> error = Flux.error(new UnsupportedOperationException("Simulated"));

        //when
        //rezultat częśćiowy np. z bazy wychodzi 100 rekordów i wyjątek
        //to właśnie tak to zostanie wrócone, collectList to zmieni i sprawi że będzie albo wszystko albo nic
        try {
            error.collectList().block();
            failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
        } catch (UnsupportedOperationException e) {
            //then
            assertThat(e).hasMessage("Simulated");
        }
    }

    @Test
    public void concatTwoFluxes() throws Exception {
        //given
        final Flux<String> many = Flux.concat(
                Flux.just("Hello"),
                Flux.just("reactive", "world")
        );

        //when
        //transparentne przełączenie się pomiędzy strumieniami

        final List<String> values = many.collectList().block();

        //then
        assertThat(values).containsExactly("Hello", "reactive", "world");
		/*
many.take(1) - poprosiliśmy o jedną wartość, to ta druga w ogóle się nie wykonuje, jeśli tam są dwie ciężkie operacje
to wykona się tylko pierwsza gdybyśmy zawołali mono.take(2) to obydwa się wykonają.

concat jest sekwencyjny najpierw będzie czytał z pierwszego a gdy się skończy to z drugiego
		 */
    }

    @Test
    public void errorFluxAfterValues() throws Exception {
        //given
        final Flux<String> error = Flux.concat(
                Flux.just("Hello", "world"),
                Flux.error(new UnsupportedOperationException("Simulated"))
        );

        /*
          w reaktorze gdy dostaniemy wyjątek to jest game over, strumien nic więcej nie wyemituje

         */

        //when
        try {
            error.collectList().block();
            failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
        } catch (UnsupportedOperationException e) {
            //then
            assertThat(e).hasMessage("Simulated");
        }
    }

    @Test
    public void fluxIsEager() throws Exception {
        //given
        AtomicInteger counter = new AtomicInteger();

        /*
        just jest gorliwy
         */
        //when
        Flux.just(counter.incrementAndGet(), counter.incrementAndGet());

        //then
        assertThat(counter).hasValue(2);
    }

    @Test
    public void fluxIsLazy() throws Exception {
        //given
        AtomicInteger c = new AtomicInteger();

        //when
        //sposob leniwy ale tylko dlatego że strumień jest stworzony inline, jeśli
        //chcielibyśmy zrobić lambdę na zewnątrz to strumień najpierws się stworzy i nie będzie leniwie
        Flux.fromStream(() -> Stream.of(c.incrementAndGet(), c.incrementAndGet()));

        //then
        assertThat(c.get()).isZero();
    }

    @Test
    public void fluxComputesManyTimes() throws Exception {
        //given
        AtomicInteger c = new AtomicInteger();
        final Flux<Integer> flux = Flux.fromStream(() ->
                Stream.of(c.incrementAndGet(), c.incrementAndGet()));

        //when
        final List<Integer> first = flux.collectList().block();
        final List<Integer> second = flux.collectList().block();

        //then
        assertThat(c).hasValue(4);
        assertThat(first).containsExactly(1, 2);
        assertThat(second).containsExactly(3, 4);
    }

    /**
     * TODO Make sure Flux is computed only once
     * Hint: {@link Flux#cache()}
     */
    @Test
    public void makeLazyComputeOnlyOnce() throws Exception {
        //given
        AtomicInteger c = new AtomicInteger();
        Flux<Integer> flux = Flux.fromStream(() ->
                Stream.of(c.incrementAndGet(), c.incrementAndGet()));

        /*
        gdy do dwóch subskrybentów a potem dołacza nam trzeci to dostaje najpier to co oni dostali wcześniej
        a ptotem już razem
         */
        //when
        final List<Integer> first = flux.collectList().block();
        final List<Integer> second = flux.collectList().block();

        //then
        assertThat(c).hasValue(2);
        assertThat(first).isEqualTo(second);
    }

}

package com.nurkiewicz.reactor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.nurkiewicz.reactor.samples.Sleeper;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;

import static reactor.core.scheduler.Schedulers.newBoundedElastic;

/*

 */
public class R080_Backpressure {

    private static final Logger log = LoggerFactory.getLogger(R080_Backpressure.class);

    @Test
    public void whatIsBackpressure() throws Exception {
        //given
        /* przy backpressure
        * onBackPressureBuffer() - gdy nie jesteśmy w stanie obsłużyć zdarzeń, buforuje zdarzenia, dzięki temu możemy
        * obsłużyć pik zdarzeń, możemy okreslić co ma się zdarzyć jeśli ten bufor również się przepełni, podaje się
        * drugi parametr BuferOverflowStrategy , jest ryzyko że zamaskuje nam problem i system wywali się po wszystkim
        *
        * jeśli pomiędzy producentem i konsumentem jest bariera wątków, gdyby nie było publishOn, nie ma żadnych
        * buforów to zarequestowane zostałoby Long.max
        *
        * Ponieważ Flux.interval() produkuje na wątku parallel to wtedy konsumpcja i produkcja odbywa się na tym
        * samym wątku . czyli producent może wyprodukować dopiero wtedy gddy producent zakończy obsługę.
        *
        * operatory publishOn i subscribeOn pełnią rolę kolejki i mają te same problemy jakie wprowadzają kolejkę
        *
        * publishOn() wskazuje pulę w któ®ej wykonają się operatory poniżej jego użycia co znaczy że nie da
        * się go wykorzystać do osadzenia na tym wątku emisji eventów. subscribeOn(), działa do góry więc tutaj
        * wykorzystany zostanie wątek z subscribeOn()
        * */
        Hooks.onOperatorDebug();
        final Flux<Long> flux = Flux
                .interval(Duration.ofMillis(10))
                .doOnError(e -> log.error("Error", e))
                .onBackpressureDrop(x -> log.warn("Dropped {}", x))
                .doOnNext(x -> log.info("Emitting {}", x))
                .doOnRequest(n -> log.info("Requested {}", n))
                .publishOn(newBoundedElastic(10 , 10, "Subscriber"))
                .doOnNext(x -> log.info("Handling {}", x))
                ;

        //when
        flux.subscribe(x -> {
                    Sleeper.sleep(Duration.ofMillis(100));
                },
                e -> {
                    log.error("Opps", e);
                }
        );

        //then
        TimeUnit.SECONDS.sleep(50);
    }
}

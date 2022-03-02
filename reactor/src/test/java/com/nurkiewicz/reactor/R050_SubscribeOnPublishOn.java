package com.nurkiewicz.reactor;

import com.nurkiewicz.reactor.samples.CacheServer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


public class R050_SubscribeOnPublishOn {

    private static final Logger log = LoggerFactory.getLogger(R050_SubscribeOnPublishOn.class);

    private final CacheServer reliable = new CacheServer("foo", Duration.ofMillis(1_000), 0);

    @Test
    public void sameThread() throws Exception {
        final Mono<String> one = Mono.fromCallable(() -> reliable.findBlocking(41));
        final Mono<String> two = Mono.fromCallable(() -> reliable.findBlocking(42));

        log.info("Starting");
        one.subscribe(x -> log.info("Got from one: {}", x));
        log.info("Got first response");
        two.subscribe(x -> log.info("Got from two: {}", x));
        log.info("Got second response");
    }

    @Test
    public void subscribeOn() throws Exception {
        Mono
                .fromCallable(() -> reliable.findBlocking(41))

                //dajemy pulę wątków, musi być gdziekolwiek pomiędzy fromCallable() a subscrive(),
                //sygnał subscribe idzie od dołu do góry i poprosi o jeden wątek , dostanie go z subscribeOn
                //potem to co jest w fromCallable() uruchomi się w tym wątku . Wątek ten został porwany przez wszystkie
                //operatory które są poniżej. Dzieje się tak ze względu na optymalizację kosztu przełączania wątku.
                //gdy raz dostanie wątek to będzie go się trzymał tak długo jak będzie mógł.
                //subscribeOn może być w dowolnym miejscu w pipe, to nie ma znaczenia. Jeśli będą dwa subscribeOn w puli
                // to wygrywa tem który jest bliżej góry pipelinea.

                .subscribeOn(Schedulers.newBoundedElastic(10, 100, "SubscribeOn"))
                .doOnNext(x -> log.info("Received {}", x))
                .map(x -> {
                    log.info("Mapping {}", x);
                    return x;
                })
                .filter(x -> {
                    log.info("Filtering {}", x);
                    return true;
                })
                .doOnNext(x -> log.info("Still here {}", x))
                .subscribe(x -> log.info("Finally received {}", x));

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    public void manyStreamsButLastThreadWins() throws Exception {
        final Mono<String> one = Mono.fromCallable(() -> reliable.findBlocking(41));
        final Mono<String> two = Mono.fromCallable(() -> reliable.findBlocking(42));

        Mono
                .zip(
                        one.subscribeOn(Schedulers.newBoundedElastic(10, 100, "A")),
                        two.subscribeOn(Schedulers.newBoundedElastic(10, 100, "B"))
                )
                .doOnNext(x -> log.info("Received {}", x))
                .map(x -> {
                    log.info("Mapping {}", x);
                    return x;
                })
                .filter(x -> {
                    log.info("Filtering {}", x);
                    return true;
                })
                .doOnNext(x -> log.info("Still here {}", x))
                .subscribe(x -> log.info("Finally received {}", x));

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    public void publishOn() throws Exception {
        Mono
                .fromCallable(() -> reliable.findBlocking(41))
                .subscribeOn(Schedulers.newBoundedElastic(10, 100, "A"))
                .doOnNext(x -> log.info("Received {}", x))
                /*
                 * publishOn() można używać wiele razy i ma to sens, jest istotne gdzie się go imieści. publishOn()
                 * działa od momentu gdy się pojawi w dół. Czyli reactor wędrując w dół pipea trzyma się wątku który
                 * dostał z subscribeOn() aż dotrze do publishOn(). Wtedy reszta dzieje się w wątku nowwym uzyskanym z
                 *  publishOn(). Przełączanie pomiędzy pulami jest bardzo kosztowne, może się zdarzyć że któraś z pul
                 *  będzie zapchana. Używa się go rzadko ponieważ : większość akcji w operatorach jest mała, nie
                 * opłaca się dedykowania mu wątku, jeśli logika jest ciężka to i tak trzeba użyć flatMapa który i
                 * tak posługuje się opóźnionymi Mono.
                 * I tak naprawdę wielowątkowość można zapiąć tam i nie używać publishOn():
                 *
                 * Mono<User> user=null;
                 * user.flatMap(usr->Mono.fromCallable(()->restTemplate.getForEntity(usr.getId()))
                 * .subscribeOn(restTemplateSomeServiceScheduler())
                 *
                 * Technicznie rzecz biorąc schedulerów powinno być tyle ile aplikacji z którymi się komunikujemy.
                 *
                 * SubscribeOn i PublisOn interesuje nas tylko wtedy gdy mamy do czynienia z api blokującym.
                 *
                 * */
                .publishOn(Schedulers.newBoundedElastic(10, 100, "B"))
                .map(x -> {
                    log.info("Mapping {}", x);
                    return x;
                })
                .publishOn(Schedulers.newBoundedElastic(10, 100, "C"))
                .filter(x -> {
                    log.info("Filtering {}", x);
                    return true;
                })
                .publishOn(Schedulers.newBoundedElastic(10, 100, "D"))
                .doOnNext(x -> log.info("Still here {}", x))
                .publishOn(Schedulers.newBoundedElastic(10, 100, "E"))
                .subscribe(x -> log.info("Finally received {}", x));

        TimeUnit.SECONDS.sleep(2);
    }

}

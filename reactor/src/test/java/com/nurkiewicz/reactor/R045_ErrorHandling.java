package com.nurkiewicz.reactor;

import com.nurkiewicz.reactor.samples.CacheServer;
import com.nurkiewicz.reactor.user.User;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operaqtory onError() działają tylko na błędy które pojawią się POYŻEJ operatora
 * onErrorReturn()= try{
 * <p>
 * }catch(){
 * return "Error"
 * }
 * onErrorReturn() może zostać zwrócony tylko dla określonych wyjątków
 * <p>
 * onErrorResume() - działa w konkteście całego Mono, jako fallback może przyjąć inną metodę asynchroniczną, np.
 * mamy dwa serwery szybki ale zawodny i wolny ale skuteczny , wtedy ten szybki może jako onErrorResume może zostać
 * wskazane wwołanie wolnego ale skutecznego
 * <p>
 * <p>
 * users
 * .flatMap(user ->
 * lastOrderOf(user)
 * .onErrorReturn(Order.FAILED)
 * )
 * .onErrorReturn(Order.FAILED)
 * .subscribe(order1, order2, FAILED, order4, order5, ...)
 * <p>
 * gdyby lini 4 nie było to a jest 100 użytkownikow i wysypujemy się przy 4 , czyli flatMap zakończy się przedwcześnie
 * <p>
 * gdyby obsługa była tylko w 4 linii. 3 się wywala, poleci wyjątek i od razu zamieniony na fallback i cały flatMap
 * będzie poprawny, czyli flatMap będzie kontynuował wywołanie, czyli gdy wywali się 3 i 77 to Order.Failed będzie
 * tylko dla nich  , reszta się przetworzy
 * <p>
 * ddolny onErrorReturn ma sens o ile z users też poleci wyjątek, gdyby tego nie było nie weszlibyśmy do flatMap, a
 * tak zostanie przechwycone i przetwarzanie zostanie wznowione
 * <p>
 * <p>
 * <p>
 * retry(), jeśli z jakiegokolwiek operatora pojawi się wyjątek to operator zostanie odsubskrybuje i zasubskrybuje
 * do mono na nowo, to spowoduje ponowienie kodu biznesowego, metoda ta pożera wyjątek , żeby go zobaczyć .doOnError()
 * cacheServer().findBy1).retry(4).doOnError(e-log.warn("wyjatek")) - w tym przypadku zalogowany będzie tylko 1 błąd
 * (5 wystąpienie bo 4 poprzednie zostaną przechwychone i zjedzone
 */
public class R045_ErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(R045_ErrorHandling.class);

    @Test
    public void onErrorReturn() throws Exception {
        //given
        final Mono<String> err = Mono.error(new RuntimeException("Opps"));

        //when
        final Mono<String> withFallback = err
                .onErrorReturn("Fallback");

        //then
        err
                .as(StepVerifier::create)
                .verifyErrorMessage("Opps");
        withFallback
                .as(StepVerifier::create)
                .expectNext("Fallback")
                .verifyComplete();
    }

    /**
     * TODO Where's the 'Failed' exception? Add some logging
     */
    @Test
    public void onErrorResume() throws Exception {
        //given
        AtomicBoolean cheapFlag = new AtomicBoolean();
        AtomicBoolean expensiveFlag = new AtomicBoolean();

        Mono<String> cheapButDangerous = Mono.fromCallable(() -> {
            cheapFlag.set(true);
            throw new RuntimeException("Failed");
        });

        Mono<String> expensive = Mono.fromCallable(() -> {
            expensiveFlag.set(true);
            return "Expensive";
        });

        //when
        final Mono<String> withError = cheapButDangerous
                .onErrorResume(e -> expensive);

        //then
        withError
                .as(StepVerifier::create)
                .expectNext("Expensive")
                .verifyComplete();
        assertThat(cheapFlag).isTrue();
        assertThat(expensiveFlag).isTrue();
    }

    /**
     * TODO Return different value for {@link IllegalStateException} and different for {@link IllegalArgumentException}
     *
     * @throws Exception
     */
    @Test
    public void handleExceptionsDifferently() throws Exception {
        handle(danger(1))
                .as(StepVerifier::create)
                .expectNext(-1)
                .verifyComplete();

        handle(danger(2))
                .as(StepVerifier::create)
                .expectNext(-2)
                .verifyComplete();

        handle(danger(3))
                .as(StepVerifier::create)
                .verifyErrorMessage("Other: 3");
    }

    /**
     * TODO Add error handling
     *
     * @see Mono#onErrorResume(Function)
     */
    private Mono<Integer> handle(Mono<Integer> careful) {
        return careful;
    }

    private Mono<Integer> danger(int id) {
        return Mono.fromCallable(() -> {
            switch (id) {
                case 1:
                    throw new IllegalArgumentException("One");
                case 2:
                    throw new IllegalStateException("Two");
                default:
                    throw new RuntimeException("Other: " + id);
            }
        });
    }

    @Test
    public void simpleRetry() throws Exception {
        //given
        CacheServer cacheServer = new CacheServer("foo.com", Duration.ofMillis(500), 1);

        //when
        final Mono<String> retried = cacheServer
                .findBy(1)
                .retry(4);

        //then
        retried
                .as(StepVerifier::create)
                .verifyErrorMessage("Simulated fault");
    }

    /**
     * TODO Why this test never finishes? Add some logging and fix {@link #broken()} method.
     * <p>
     * Problem polega na tym że metoda zwracająca Mono udaje że jest opóźniona a powinno tak być , nie powinna
     * od razu robić logiki, powinna zwracać leniwe mono a dopiero po subskrypcji ma się wykonać logika
     * <p>
     * Trzeba sprawić by metoda broken stała się leniwa. Wykorzystujemy fromCallable. metoda broken() zostaje
     * zamieniona no notBroken()
     */
    @Test
    public void fixEagerMono() throws Exception {
        //given
        final Mono<User> mono = notBroken();

        //when
        final Mono<User> retried = mono.doOnError(it -> log.warn(it.getMessage())).retry();

        //then
        retried
                .as(StepVerifier::create)
                .expectNext(new User(1))
                .verifyComplete();
    }

    Mono<User> broken() {
        double rand = ThreadLocalRandom.current().nextDouble();
        if (rand > 0.1) {
            return Mono.error(new RuntimeException("Too big value " + rand));
        }
        return Mono.just(new User(1));
    }

    Mono<User> notBroken() {
        return Mono.fromCallable(() -> {
                    double rand = ThreadLocalRandom.current().nextDouble();
                    if (rand > 0.1) {
                        throw new RuntimeException("Too big value " + rand);
                    }
                    return new User(1);
                }
        );
    }

    /*
    realizacja ponawiania coś podobnego jak resilience4j , żądanie będzie ponawiane jeśli się nie uda
     */
    @Test
    public void retryWithExponentialBackoff() throws Exception {
        final RetryBackoffSpec spec = Retry
                //wprowadzamy opóźnienie
                .backoff(20, Duration.ofMillis(100))
                //opóźnienia będą mniej lub bardziej losowe
                .jitter(0.2)
                .maxBackoff(Duration.ofSeconds(2));
        Mono
                .error(new RuntimeException("Opps"))
                .doOnError(x -> log.warn("Exception: {}", x.toString()))
                .retryWhen(spec)
                .subscribe();
        TimeUnit.SECONDS.sleep(5);
    }

}

package com.nurkiewicz.reactor;

import com.nurkiewicz.reactor.domains.Crawler;
import com.nurkiewicz.reactor.domains.Domain;
import com.nurkiewicz.reactor.domains.Domains;
import com.nurkiewicz.reactor.domains.Html;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;


public class R053_Concurrency {

    private static final Logger log = LoggerFactory.getLogger(R053_Concurrency.class);

    /**
     * TODO Crawl 500 domains as soon as possible.
     * <p>
     * Use {@link Crawler#crawlBlocking(Domain)}
     * </p>
     *
     * @see Flux#subscribeOn(Scheduler)
     */
    @Test(timeout = 10_000L)
    public void crawlConcurrently() throws Exception {
        //given
        final Flux<Domain> domains = Domains
                .all()
                .doOnSubscribe(s -> log.info("About to load file"));

		/*
				Ladowanie pliku idzie osobnym wątku bo subscribeOn bo tak naprawdę strumieniem wejściowym jest domains,
				flatMap również dostał wątek i w nim stworzył
				500 oddzielnych Mono. Niestety wszystki są blokujące, bo nigdzie nie zostało powiedziane że kazde mono
				 ma być w osobnym  wątku. Mono.fromCallable(...) nie dodaje wielowątkowości, wywołanie na tym
				 subscribe() będzie blokujące, dlatego w tym przypadku FlatMap sekwencyjnie wykonuje subscribe()  na
				 każdym mono (robi merge), ale dzieje się to jedno po  drugim.
				FlatMap (a dokładniej wykonywany przez niego merge) zakłada, że wszystkie Mono są albo bardzo szybkie,
				albo blokujące.

				domains
				.subscribeOn(Schedulers.newBoundedElastic(50, 100, "A"))
				.flatMap(domain->Mono.fromCallable(()->Crawler.crawlBlocking(domain)));

				Inny błąd:
				domains
				.subscribeOn(Schedulers.newBoundedElastic(50, 100, "A"))
				.map(domain->Crawler.crawlBlocking(domain));
				parametrem map jest jednowątkowe i zanim cokolwiek się stanie wywoła się metoda - jest to typowy kod
				blokujący

				Żeby
	*/

        //musi być wyniesiony na zewnątrz, bo chcemy by była ona współdzielona
        //jeśli byłaby wewnątrz subscribeOn() to byłaby ona stworzona dla każdej z 500 domen
        //UWAGA! takie rozwiązanie to ogromny wyciek pamięci! Dlatego właśnie pule powinny być zarządzane przez Spring
        Scheduler CrawlerSchedulersPool = Schedulers.newBoundedElastic(100, 100, "A");


        //when
        final Flux<Html> htmls =
                domains
                        .flatMap(domain -> Mono.fromCallable(() -> Crawler.crawlBlocking(domain))
                                .subscribeOn(CrawlerSchedulersPool));

        //then
        //Map może odpalić się na wielu wątkach (dostarczonych z flatMap()) ale niekoniecznie na tym samym wątku co
        // mono - dzieje się tak ze względu na optymalizację wątków reactora
        final List<String> strings = htmls.map(html -> {
            //tu wewnątrz nie ma żadnego zrównoleglenia - zawsze jeden wątek - ten w którym wykonuje się map
            log.info(html.getRaw());
            return html.getRaw();
        }).collectList().block();
        assertThat(strings)
                .hasSize(500)
                .contains("<html><title>http://mozilla.org</title></html>");
    }

    /**
     * TODO Just like above, but return {@link Tuple2} with both {@link Domain} and {@link Html}.
     * You <strong>must</strong> use {@link Crawler#crawlAsync(Domain)}
     *
     * @see Tuples#of(Object, Object)
     */
    @Test(timeout = 10_000L)
    public void knowWhereHtmlCameFrom() throws Exception {
        //given
        final Flux<Domain> domains = Domains.all();

        //when
        final Flux<Tuple2<URI, Html>> tuples  = domains
                .flatMap(domain ->Crawler.crawlAsync(domain).map(it->Tuples.of(domain.getUri(),it)));

        //then
        final List<Tuple2<URI, Html>> list = tuples
                .collectList()
                .block();

        assertThat(list)
                .hasSize(500)
                .contains(Tuples.of(new URI("http://archive.org"), new Html("<html><title>http://archive.org</title></html>")))
                .contains(Tuples.of(new URI("http://github.com"), new Html("<html><title>http://github.com</title></html>")));

        list.forEach(pair ->
                assertThat(pair.getT2().getRaw()).contains(pair.getT1().getHost()));
    }

    /**
     * TODO Just like above, but return {@link Mono} with {@link Map} inside.
     * Key in that map should be a {@link URI} (why not {@link java.net.URL}?), a value is {@link Html}
     *
     * @see Flux#collectMap(Function, Function)
     */
    @Test(timeout = 10_000L)
    public void downloadAllAndConvertToJavaMap() throws Exception {
        //given
        final Flux<Domain> domains = Domains.all();

        //when
        /*
        URL jest dziwnie zaimplementowany że equals sprawdza ip hosta do któ®ego prowadzi, więc resolvuje domenę i robi
        zapytanie sieciowe co jest dramatyczne jeśli chodzi o użycie jako klucz w mapie.

       UWAGA!!!
       Gdy mamy dostęp do sieci to obiekty mogą być równe, natomiast jeśli nie ma dostępu zwracany jest fallback i
       obiekty mogą być już nierówne.

         */
        final Mono<Map<URI, Html>> mapStream =
                domains.flatMap(d->Crawler.crawlAsync(d).map(html->Tuples.of(d.getUri(),html))).collectMap(Tuple2::getT1,Tuple2::getT2);

        //then
        final Map<URI, Html> map = mapStream.block();

        assertThat(map)
                .hasSize(500)
                .containsEntry(new URI("http://archive.org"), new Html("<html><title>http://archive.org</title></html>"))
                .containsEntry(new URI("http://github.com"), new Html("<html><title>http://github.com</title></html>"));

        map.forEach((key, value) ->
                assertThat(value.getRaw()).contains(key.getHost())
        );
    }

    /**
     * TODO Copy-paste solution from first test, but replace {@link Crawler#crawlBlocking(Domain)} with {@link Crawler#crawlThrottled(Domain)}.
     * Your test should fail with "Too many concurrent crawlers" exception.
     * How to prevent {@link Flux#flatMap(Function)} from crawling too many domains at once?
     *
     * @see Flux#flatMap(Function, int)
     */
    @Test(timeout = 20_000L)
    public void throttledDownload() throws Exception {
        //given
        Scheduler CrawlerSchedulersPool = Schedulers.newBoundedElastic(100, 100, "A");

        final Flux<Domain> domains = Domains
                .all();
/*
 FlatMap próbuje się zasubskrybować do wszystkich map pod spodem, flatMap może wykonać np. 500 zapytań sieciowych,
 flatMap w takiej sytuacji wykona DDoS.

 Zeby się przed tym zabezpieczyć można podać do flatMap drugi parametr,tym razem określający wielkość paczki.
 Jest to liczba jednoczesnych subskrypcji, które wykona flatMap.

 Alternatywnie można zmniejszyć pulę wątków
 Scheduler CrawlerSchedulersPool = Schedulers.newBoundedElastic(50, 100, "A");

 Lepsze rozwiązanie jest parametr do flatMap, ponieważ w teorii reactor nie wie jak duży jest scheduler, czesem
 zarządzamy schedulerem z zewnątrz, pozatym przy programowaniu nieblokującym często nie używa się schedulerów więc
 zostaje tylko parametr na flatMap

flatMap ma domyślny limit jednoczesnych subskrypcji to 256, czyli jeśli nie podamy nic to stworzy maksymalnie 256
wątków, jeśli zwiększymy i podamy 500, to oczywiście stworzy się więcej.
* */
        //when
        final Flux<Html> htmls =
                domains
                        .flatMap(domain -> Mono.fromCallable(() -> Crawler.crawlThrottled(domain))
                                .subscribeOn(CrawlerSchedulersPool),50);

        //then
        final List<String> strings = htmls.map(Html::getRaw).collectList().block();
        assertThat(strings)
                .hasSize(500)
                .contains("<html><title>http://mozilla.org</title></html>");
    }

    /**
     * TODO Generate list of tuples, but this time by zipping ({@link Flux#zip(Publisher, Publisher)})
     * stream of domains with stream of responses.
     * Why does it fail?
     */
    @Test
    public void zipIsBroken() throws Exception {
        //given
        final Flux<Domain> domains = Domains.all();

        //when
        Flux<Html> responses = null; // TODO
        final Flux<Tuple2<URI, Html>> tuples = Flux.zip(
                domains.map(Domain::getUri),
                responses
        );

        //then
        final List<Tuple2<URI, Html>> list = tuples
                .collectList()
                .block();

        assertThat(list)
                .hasSize(500)
                .contains(Tuples.of(new URI("http://archive.org"), new Html("<html><title>http://archive.org</title></html>")))
                .contains(Tuples.of(new URI("http://github.com"), new Html("<html><title>http://github.com</title></html>")));

        list.forEach(pair ->
                assertThat(pair.getT2().getRaw()).contains(pair.getT1().getHost()));
    }

    /**
     * TODO Generate list of tuples, but this time by zipping ({@link Flux#zip(Publisher, Publisher)})
     *
     * @see Flux#flatMapSequential(Function)
     */
    @Test
    public void zipIsNotBrokenIfUsedWithFlatMapSequential() throws Exception {
        //given
        final Flux<Domain> domains = Domains.all();

        //when
        /*
        rozwiązanie złe, ponieważ pomiesza domeny i html zostaną połączone losowo, w zależności od tego co pojawi się
        na zwrotce aktualnie.

        Flux<Html> responses = null; // TODO
        final Flux<Tuple2<URI, Html>> tuples = Flux.zip(
                domains.map(Domain::getUri),
                domains.flatMap(Crawler::crawlAsync)
        );

        Można skorzystać z flatMapSequential() zachowuje on kolejność odpowiedzi.

        Gdy pobieramy coś dużego i pierwszy url jest najwolniejszy a pozostałem 999 już się przetworzyły.
        Trzymamy te wszystkie obrazki w pamięci. i nic nie możemy zrobić bo czekamy na pierwszy. Jeśli skończy się
        timeoutem to na wyjściu zobaczymy tylko ten wyjątek.

        Dlatego też trzeba bardzo uważać.
        */

        Flux<Html> responses = null; // TODO

        final Flux<Tuple2<URI, Html>> tuples = Flux.zip(
                domains.map(Domain::getUri),
                domains.flatMapSequential(Crawler::crawlAsync)
        );

        //then
        final List<Tuple2<URI, Html>> list = tuples
                .collectList()
                .block();

        assertThat(list)
                .hasSize(500)
                .contains(Tuples.of(new URI("http://archive.org"), new Html("<html><title>http://archive.org</title></html>")))
                .contains(Tuples.of(new URI("http://github.com"), new Html("<html><title>http://github.com</title></html>")));

        list.forEach(pair ->
                assertThat(pair.getT2().getRaw()).contains(pair.getT1().getHost()));
    }

}

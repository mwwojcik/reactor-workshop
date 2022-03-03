# Reactor training

[![Java CI master](https://github.com/nurkiewicz/reactor-workshop/actions/workflows/gradle.yml/badge.svg)](https://github.com/nurkiewicz/reactor-workshop/actions/workflows/gradle.yml) [![Java CI solutions](https://github.com/nurkiewicz/reactor-workshop/actions/workflows/gradle.yml/badge.svg?branch=solutions)](https://github.com/nurkiewicz/reactor-workshop/actions/workflows/gradle.yml)

Spring [Reactor](https://projectreactor.io) hands-on training (3 days)

See also [workshop notes](https://nurkiewicz.com/slides/reactor-workshop).

## Day 1: Introduction test

- What is reactive programming
- Crash course to `CompletableFuture` and thread pools
- Introducing Reactor
- How to create a stream?
  - `just()`, `generate()`, `create()`, `fromCallable()`, `fromStream()`
- Laziness
  - Hot vs. cold
- Basic operators
  - `map()`, `filter()`, `filterWhen()` `flatMap()`, `handle()`, `take()`, `skip()`
  - `doOn*()` operators
  - `window()`, `buffer()`, `distinct()`
  - `cast()`, `ofType()`, `index()`
  - `timestamp()`, `elapsed()`
  - `zip()`, `merge()`
- Error handling
  - `timeout()`, `retry*()`, `retryBackoff()`
  - `onError*()`
- Blocking and reactive, back and forth
- Concurrency with blocking code and thread pools
  - `subscribeOn()`, `parallel()`
- Unit testing

## Day 2: Reactor advanced
- Concurrency with non-blocking code
- Advanced error handling and retries
- `transform()` vs. `transformDeferred()`
- Advanced operators
  - `groupBy()`, `window()`
  - `reduce()`, `scan()`
  - `expand*()`
- Backpressure
  - `onBackpressure*()`
- `Processor` API
  - `Unicast`, `Emitter`, `Replay`
- Advanced testing with virtual time
- `Context`
- Speculative execution example
- [RxJava](https://github.com/ReactiveX/RxJava) interoperability

## Day 3: Practical
- Comparison to blocking and asynchronous servlets
- Refactoring existing application to Reactor
- [Spring Boot](https://spring.io/projects/spring-boot)
  - Reactive database access
  - Reactive controllers
  - `WebFilter`
  - Global error handling
  - Payload validation
  - Web sockets
- Streaming data in and out
- Troubleshooting and debugging
  - `checkpoint()`, `onOperatorDebug()`, `doOn*()`

## Reference materials

1. [Reactor 3 Reference Guide](https://projectreactor.io/docs/core/release/reference/)
2. [Web on Reactive Stack](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#spring-webflux) in [Spring Framework Documentation](https://docs.spring.io/spring/docs/current/spring-framework-reference/index.html)
3. [The "Spring WebFlux Framework"](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-developing-web-applications.html#boot-features-webflux) in [Spring Boot Reference Guide](https://docs.spring.io/spring-boot/docs/current/reference/html/)

## Troubleshooting

### IntelliJ test runner

In IntelliJ it's much faster to run tests directly, rather than through Gradle.
Go to `Preferences` -> `Build, Execution, Deployment` -> `Build Tools` -> `Gradle` and select `IntelliJ IDEA` from `Run Tests Using` drop-down.

### Error `Can not connect to Ryuk at localhost:...`

Add this environment variable:

```
TESTCONTAINERS_RYUK_DISABLED=true
```

See: [Disabling Ryuk](https://www.testcontainers.org/features/configuration/#disabling-ryuk)

## Webflux
### Wstęp
Nieblokujące api, gdy mamy połączenie na sockecie możemy w łatwy tani sposób odpytywać czy na którymś sockecie nie 
pojawiły się dane. Możemy mieć kilka tysięcy połączeń, oraz kilka wątków które sprawdzają czy na socketach nie 
pojawiły się dane. 
W klasycznym modelu na połączenie potrzebujemy jednego wątku. Czyli dla 10k połączeń potrzebujemy 10k wątków (czyli 
ok 1 GB ramu ~ 1 MB per wątek), czyli 10 k tasków systemu operacyjnego.

Połączenie nieblokujące - pod spodem nie używa socketów na których trzeba stać i czekać, tylko korzysta ze zdarzeń 
że na sockecie pojawiło się dane. Informacje przesyła system.

Z poziomu Linuxa, otwarte połączenie sieciowe to deskryptor pliku (struktura danych w SO mówiąca o połączeniu z 
danym hostem) + bufor odpow. za wysyłanie i odbieranie to ok 1KB+bufor. To jest wszystko, reszta to aplikacja. Po 
stronie SO nie trzeba utrzymywać żadnych wątków do obsługi.

Przychodzi do serwera paczka danych TCPIP, karta go odbiera i informuje SO że przyszła paczka z takiego hosta, SO 
wyszukuje socket, znajduje proces i przesyła te dane, przy nieblokujące java api (NIO) dostaje zdarzenie że coś się 
pojawiło, jesteśmy już po stronie JVM . Java NIO jest bardzo niskopoziomowa więc wchodzi w grę biblioteka Netty, 
bardzo rozpowszechniona, nakładka na sockety. Mówi że na tym sockecie pojawiło się zdarzenie. W Netty odbiera się 
żywe tablice, paczki dane. Netty dostarcza abstrakcji zamieniające bajty , pakiety w stringi i coraz większe 
abstrakcje. Przy pomocy pipelineów można stworzyć bardzo wydajny serwerek. 
Na bazie Netty Reactor zbudował swoją abstrakcję Reactor-Netty, która zamienia strumienie bajtowe, tworzy abstrakcje
reaktywne Flux i Netty. Dopiero na tym jest zbudowany WebFlux. 

Nie ma żadnego klasycznego serwera obsługującego http, jetty tomcat, w aplikacji webflux nie ma nawet zależności do 
sewletów. 

Klasycznie: odbieramy połączenie, potem dedykowany wątek, najpierw był dispatcher, potem kontroler , potem serwisy, 
repozytoria, gdy wracalismy do kontrolera, potem serwlet, potem zamiana na bajty i dopiero wątek był zwalniany. 

Prawo Little'a : Mówi o szacowaniu ilości potrzebnych wątków workerów na podstawie średniego czasu odpowiedzi i 
ilości ruchu. To prawo ma konsekwencję . Jeśli czas odpowiedzi jednego z komponentów wzsrasta przekłada się na 
mniejszy ruch obsługiwany. To że odpowiadamy wolniej to oczywiste, ale dlaczego możemy przyjąć mniej ruchu ? To nie 
jest oczywiste. Serwery klasyczne dłużej wiszą, dłużej czeka, nie wykonuje więcej pracy , on nic nie robi, czeka na 
wolniejszy komponent, ale z zewnątrz nei obsługuje ruchu. Aplikacja nie robi nic więcej a przepustowość znacząco 
spada. Wisimy na sockecie. Ten problem próbuje rozwiązać webflux. 
jesteśmy zdarzeniowi, mamy kilka wątków np. 4 , np. wychodzą dane do wolnego komponentu ,wątek biegnie dalej,  wątek 
wybudza się gdy 
dostanie info że przyszły dane np. po 2 sekundach wątek to rejestruje i przesyła odpowiedź. Ale w tym czasie nie 
czeka tylko obsługuje innych.
Webflux nie sprawi że aplikacja będzie szybsza bo baza i tak nie odpowie szybciej. Ale jednocześnie może być 
obsłużona dużo większa ilość klientów. 

Webflux RestController zwraca mono ale jest on niezasubskrybowany, zwracamy mono/flux ale odczytaniem zajmuje się 
już Spring. Jedyne co musi zrobić to stworzyć leniwego Mono, samo tworzenie leniwego mono to jest mikroskopijny czas, 
WebFlux dzięki temu może stworzyć takich Mono mnóstwo na jednym wątku.

Kontroler zwraca tylko "przepis" deklaracja co ma się wykonać . Algorytm ten wywołany zostanie zupełnie w innym 
momencie, już poza kontrolerem przez Springa.

Z perspektywy klienta to wygląda jak czyste http. Wystawiane są endpointy. Wszystko jest zgodne z HTTP.  

W przypadku Flux skończonego uderzenie na takiego endpointa zwrócona zostanie tablica. 

W przypadku Flux nieskończonego zaczyna się problem. Pod spodem WebFlux czeka aż flux się skończy. To nie zadziała. 
Ale dorzucenie nagłówka Server-Sett Event . Wysłałem żądanie i serwer będzie przez nieskończony czas będzie dosyłał 
mi odpowiedzi.  Dane się dosyłają w nieskończoność. 

Bezstanowość nie ma prawa pamiętać stanu. Tu nie ma request i response za każdym razem. Tu jest jeden REQUEST i 
jeden RESPONSE tylko połązenie jest bardzo bardzo, bardezo długie, w skrajnym przypadku nieskończone.  

Na jednego requesta otwierane jest połączenie i jest ono trzymane a do połączenia jest wrzucany wynik flux.
Gdyby klient miał timeout założony na zwykłe połączenia to ono by się stimeaoutowało.

Jeśli chodzi o webflux problem 10k - czy na tej technologii można napisać aplikację obsługującą 10K połączeń. Na 
zwykłym tomcacie to jest niemożliwe bo wymaga 10 tysięcy wątków. Dla Webflux to jest bardzo proste. 

Jeśli uderzymy w endpoint zwracający Fluxa z błędem to dostanie jsona z błędem.

Można zwrócić w Mono / Flux ResponseEntity, wtedy można kontrolować zwrócone nagłówki. 


### WebClient

* RestTemplate - klasyczny klient http , wrapper na inne klienty - nie jest deprecated, ale nie będzie rozwijany
* AsyncRestTemplate - RestTemplate opakowany w pulę wątków , blokujący ale odpalony w osobnym wątku - jest Deprecated
* WebClient - asynchroniczny 


* **asynchroniczny** - dzieje się w tle
* **nieblokujący** - asynchroniczny - nie istnieje żaden wątek w systemie czekający na odpowiedź klienta

Przez WebFlux możemy się poołączyć do endpointa typu SSE, i zrobienie bodyToFlux spowoduje zamianę na fluxa. 
WebClient zwraca body lub flux czyli stają się źródłem mono lub flux. Na tym można robić dowolne operacje i stosować 
operatory. 

**Nigdy się nie blokujemy i nigdy się nie subskrybujemy.**



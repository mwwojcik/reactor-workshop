package com.nurkiewicz.webflux.demo.feed;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class FeedReader {

    private static final Logger log = LoggerFactory.getLogger(FeedReader.class);


    private WebClient webClient;

    public FeedReader(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * TODO (3) Return <code>Flux&lt;SyndEntry&gt;</code>
     * Start by replacing {@link #getAsync(URL)} (URL)} with {@link #getAsync(URL)}.
     * <p>
     * Czyta konkretny blog i czyta listę artykułów, chcielibyśmy by zwracała Flux, zastąpić get getAsync()
     */
    public Flux<SyndEntry> fetch(URL url) {
        return getAsync(url)
                .doOnError(SocketException.class, e -> log.warn("Error {}: {}", url, e.toString()))
                .onErrorResume(SocketException.class, e -> Mono.empty())
                .flatMap(it -> parseFeed(it))
                .flatMapIterable(entries -> entries);


    }

    private Mono<List<SyndEntry>> parseFeed(String feedBody) {
        return Mono.fromCallable(() -> {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            ByteArrayInputStream is = new ByteArrayInputStream(applyAtomNamespaceFix(feedBody).getBytes(UTF_8));
            Document doc = builder.parse(is);
            SyndFeedInput input = new SyndFeedInput();
            return input.build(doc).getEntries();
        });
    }


    private String applyAtomNamespaceFix(String feedBody) {
        return feedBody.replace("https://www.w3.org/2005/Atom", "http://www.w3.org/2005/Atom");
    }

    /*private String get(URL url) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn.getResponseCode() == HttpStatus.MOVED_PERMANENTLY.value()) {
            return get(new URL(conn.getHeaderField("Location")));
        }
        try (final InputStreamReader reader = new InputStreamReader(conn.getInputStream(), UTF_8)) {
            return CharStreams.toString(reader);
        }
    }*/

    /**
     * TODO (2) Load data asynchronously using {@link org.springframework.web.reactive.function.client.WebClient}
     *
     * @see <a href="https://stackoverflow.com/questions/47655789/how-to-make-reactive-webclient-follow-3xx-redirects">How to make reactive webclient follow 3XX-redirects?</a>
     */
    Mono<String> getAsync(URL url) {
        Mono<URI> myUri = Mono.fromCallable(() -> url.toURI());
        //ten flatmap jest po to że konieczne jest przemapowanie URL na URI
        //niestety jest tam checked wyjąek. opakowanie tego w mono pozwala na
        //obsługę checked wyjątku bo fromCallable zamieni go na Mono.error()
        return myUri.flatMap(uri ->
                webClient
                        .get()
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(String.class)
        );

    }
}

/*
 * dodać webclienta i musi obsłużyć przekierowania
 * */

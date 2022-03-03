package com.nurkiewicz.webflux.demo.emojis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@RestController
public class EmojiController {

    private final URI emojiTrackerUrl;
    private final WebClient webClient;

    public EmojiController(@Value("${emoji-tracker.url}") URI emojiTrackerUrl, WebClient webClient) {
        this.emojiTrackerUrl = emojiTrackerUrl;
        this.webClient = webClient;
    }

    @GetMapping(value = "/emojis/raw", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent> raw() {
        return webClient
                .get()
                .uri(emojiTrackerUrl)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class);
    }

    @GetMapping(value = "/emojis/rps", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Long> rps() {
        return webClient
                .get()
                .uri(emojiTrackerUrl)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class)
                .window(Duration.ofSeconds(1))
                .flatMap(it -> it.count());


    }

    /*
     * Example input:
     * <code>
     *   data:{"2600":1,"2728":1}
     *   data:{"1F602":1,"2600":2,"2764":1}
     *   data:{"2728":4,"2828":1}
     * </code>
     * */

    //   .bodyToFlux(new ParameterizedTypeReference<Map<String, Integer>>() {})
    @GetMapping(value = "/emojis/eps", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Integer> eps() {
        return webClient
                .get()
                .uri(emojiTrackerUrl)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Integer>>() {
                })
                //gdyby tego nie było to po window mielibyśmy strumień map
                //rozpłaszczenie go powoduje że ułatwi się sumowanie
                .flatMapIterable(Map::values)
                .window(Duration.ofSeconds(1))
                .flatMap(it -> it.reduce((accumulator, element) -> accumulator + element));

        //flatMap(f->f.reduce(Integer::sum))


    }

    /**
     * TODO Total number of each emoji (ever-growing map)
     * <p>
     * Example input:
     * <code>
     * data:{"2600":1,"2728":1}
     * data:{"1F602":1,"2600":2,"2764":1}
     * data:{"2728":4,"2828":1}
     * </code>
     * <p>
     * Example output:
     * <code>
     * data:{"2600":1}
     * data:{"2600":1,"2728":1}
     * <p>
     * data:{"2600":1,"2728":1,"1F602":1}
     * data:{"2600":3,"2728":1,"1F602":1}
     * data:{"2600":3,"2728":1,"1F602":1,"2764":1}
     * <p>
     * data:{"2600":3,"2728":5,"1F602":1,"2764":1}
     * data:{"2600":3,"2728":5,"1F602":1,"2764":1,"2828":1}
     * </code>
     */

    @GetMapping(value = "/emojis/aggregated", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Map<String, Integer>> aggregated() {
        return webClient
                .get()
                .uri(emojiTrackerUrl)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Integer>>() {
                })
                //gdyby tego nie było to po window mielibyśmy strumień map
                //rozpłaszczenie go powoduje że ułatwi się sumowanie
                .flatMapIterable(Map::entrySet)
                .scan(new HashMap<>(), (acc, entry) -> {
                    Map<String, Integer> output = new HashMap<>(acc);
                    output.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    return output;
                });
    }

    /**
     * @see #topValues(Map, int)
     */
    @GetMapping(value = "/emojis/top", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Map<String, Integer>> top(@RequestParam(defaultValue = "10", required = false) int limit) {
//        return aggregated()...
        return aggregated().map(hashMap->topValues(hashMap,10));
    }

    private <T> Map<T, Integer> topValues(Map<T, Integer> agg, int n) {
        return new HashMap<>(agg
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(reverseOrder()))
                .limit(n)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @GetMapping(value = "/emojis/topStr", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<String> topStr(@RequestParam(defaultValue = "10", required = false) int limit) {
        return top(50)
                .map(this::keysAsOneString)
                .distinctUntilChanged();
    }

    String keysAsOneString(Map<String, Integer> m) {
        return m
                .keySet()
                .stream()
                .map(EmojiController::codeToEmoji)
                .collect(Collectors.joining());
    }

    static String codeToEmoji(String hex) {
        final String[] codes = hex.split("-");
        if (codes.length == 2) {
            return hexToEmoji(codes[0]) + hexToEmoji(codes[1]);
        } else {
            return hexToEmoji(hex);
        }
    }

    private static String hexToEmoji(String hex) {
        return new String(Character.toChars(Integer.parseInt(hex, 16)));
    }

}

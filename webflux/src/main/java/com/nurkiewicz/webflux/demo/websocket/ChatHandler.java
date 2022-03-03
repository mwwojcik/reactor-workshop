package com.nurkiewicz.webflux.demo.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.okio.Sink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * TODO
 * <ol>
 *     <li>Use single sink to publish incoming messages</li>
 *     <li>Broadcast that sink to all listening subscribers</li>
 *     <li>New subscriber should receive last 5 messages before joining</li>
 *     <li>Add some logging: connecting/disconnecting, how many subscribers</li>
 * </ol>
 * Hint: Sink should hold {@link String}s, not {@link WebSocketMessage}s
 */
public class ChatHandler implements WebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);

	final Sinks.Many<String> broadcast = Sinks.many().replay().limit(5);

	/*
	* 1. wszystko co przyjdzie z przeglądarki wrzucić do sinka
	* 2. sinka wrzucić do  session
	* */

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		session
				//zwraca strumień wiadomości przychodzących - flux wiadomości przychodzących
				.receive()
				.doOnSubscribe(s -> log.info("[{}] Got new connection", session.getId()))
				//rozpakowuję message do tekstu
				.map(WebSocketMessage::getPayloadAsText)
				.doOnNext(it->broadcast.tryEmitNext(it))
				.doOnNext(it-> log.info("[{}] Subscribers count",broadcast.currentSubscriberCount()))
				//uwaga konieczny subscribe() ponieważ inny strumień otrzymujemy a inny wysyłamy
				//o subskrypcję strumienia wysyłanego zadba coś co jest po send()
				//ale ten pierwszy przychodzący musimy obsłużyć sami, dzięki tej subskrypcji eventy przejdą
				//inny strumień, gdyby nie było go to pierwszy flux który do nas przychodzi jest leniwy

				.subscribe();

		return session
				//cały strumień odsyłam do przeglądarki, strumień jest "tak jakby zawracany"
				.send(broadcast.asFlux().map(session::textMessage))
				.doOnSuccess(v -> log.info("[{}] Done, terminating the connection", session.getId()));

	}

}

package com.nurkiewicz.webflux.demo.feed;

import javax.annotation.PostConstruct;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;

import com.rometools.rome.io.FeedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.springframework.stereotype.Component;

@Component
public class FeedAggregator {

    private static final Logger log = LoggerFactory.getLogger(FeedAggregator.class);

    private final OpmlReader opmlReader;
    private final FeedReader feedReader;

    public FeedAggregator(OpmlReader opmlReader, FeedReader feedReader) {
        this.opmlReader = opmlReader;
        this.feedReader = feedReader;
    }

    /**
     * TODO (4) Read all feeds and store them into database
     * TODO (5) Repeat periodically, do not store duplicates
     *
     *
     */
    @PostConstruct
    public void init() throws IOException, FeedException, SAXException, ParserConfigurationException {
       /* opmlReader.allFeedsStream()
        feedReader.fetch(new URL(feed)).forEach(e -> {
            log.info("{}: {} at {}", e.getPublishedDate(), e.getTitle(), e.getLink());
        });*/
    }
}

//uwaa save na repository - koniecznie trzeba się zasubskrybować na mono ktore zwraca bo nie zapisze
//dlatego nie może być w map() musi być flatMap() bo flatMap się subskrybuje
//kazdy blog może być pobierany z innym interwałem

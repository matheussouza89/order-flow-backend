package com.matheus.orderFlow.integration;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoIT extends AbstractIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void shouldReachMongoThroughTheContainer() {
        Document ping = mongoTemplate.getDb().runCommand(new Document("ping", 1));

        assertEquals(1.0, ping.get("ok"));
    }
}

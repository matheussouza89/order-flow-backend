package com.matheus.orderFlow.sales;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderViewStore {

    private final MongoTemplate mongoTemplate;

    boolean replaceIfNewer(OrderView view) {
        Query storedIsOlder = Query.query(
                Criteria.where("_id").is(view.getId()).and("version").lt(view.getVersion()));

        try {
            mongoTemplate.replace(storedIsOlder, view, ReplaceOptions.replaceOptions().upsert(), OrderView.COLLECTION);
            return true;
        } catch (DuplicateKeyException sameOrNewerStored) {
            return false;
        }
    }
}

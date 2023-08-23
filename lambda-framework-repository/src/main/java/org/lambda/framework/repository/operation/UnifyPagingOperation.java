package org.lambda.framework.repository.operation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UnifyPagingOperation<Entity> {
    public Mono<Integer> count();

    public Flux<Entity> query();
}

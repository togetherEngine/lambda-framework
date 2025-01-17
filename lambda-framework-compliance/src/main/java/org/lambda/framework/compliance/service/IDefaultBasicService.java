package org.lambda.framework.compliance.service;

import org.lambda.framework.compliance.repository.po.UnifyPO;
import org.lambda.framework.repository.operation.Paged;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IDefaultBasicService<PO extends UnifyPO,ID>{

    public Mono<PO> update(PO po);

    public Mono<PO> insert(PO po);

    public Flux<PO> update(Publisher<PO> pos);

    public Flux<PO> insert(Publisher<PO> pos);

    public Mono<Void> delete(ID id);

    public Mono<Void> delete(Publisher<ID> ids);

    public Mono<Void> delete(Iterable<? extends PO> entities);
    public Flux<PO> find(PO po);

    public Flux<PO> find();

    public Mono<PO> get(ID id);

    public Mono<PO> get(PO po);

    public Mono<Paged<PO>> find(Long page, Long size, PO po);
}

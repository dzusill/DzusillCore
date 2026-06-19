package me.dzusill.core.database.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Async CRUD abstraction over a persistent collection of entities, keyed by an identifier. Sits above the raw
 * {@link me.dzusill.core.database.Database} layer to give feature code a clean, intention-revealing API; concrete
 * repositories are produced by extending {@link AbstractSqlRepository}.
 *
 * @param <ID>
 *            identifier type
 * @param <T>
 *            entity type
 */
public interface Repository<ID, T> {

    CompletableFuture<Optional<T>> find(ID id);

    CompletableFuture<List<T>> findAll();

    CompletableFuture<Void> save(T entity);

    CompletableFuture<Void> delete(ID id);

    CompletableFuture<Boolean> exists(ID id);
}

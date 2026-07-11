package io.effects.recipes.observable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An Object-Oriented "Recipe" representing an Observable Process Manager.
 * Coordinates declarative subscriptions, filtering policies, and event routing.
 */
public final class ObservableProcess<ID, S, E> {
    private final StateRepository<ID, ObservableLedger<ID, S>> repository;
    private final EventPublisher<ObservableEvent<ID, S>> publisher;
    private final EventSubscriber<E> subscriberPort;
    private final TelemetryPort telemetry;
    private final ConcurrentHashMap<S, ObservableRequest<ID, E>> subscribers = new ConcurrentHashMap<>();

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ObservableProcess(
        StateRepository<ID, ObservableLedger<ID, S>> repository,
        EventPublisher<ObservableEvent<ID, S>> publisher,
        EventSubscriber<E> subscriberPort,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.subscriberPort = Objects.requireNonNull(subscriberPort);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Default constructor using memory adapters.
     */
    public ObservableProcess(EventSubscriber<E> subscriberPort) {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), subscriberPort, new NoOpTelemetryPort());
    }

    /**
     * Registers a behavioral subscriber domain object.
     */
    public IO<Void> register(S subscriberId, ObservableRequest<ID, E> subscriber) {
        Objects.requireNonNull(subscriberId);
        Objects.requireNonNull(subscriber);
        return IO.delay(() -> {
            subscribers.put(subscriberId, subscriber);
            return null;
        });
    }

    /**
     * Declarative subscription orchestration mapping.
     */
    public IO<Either<String, ObservableLedger<ID, S>>> subscribe(ID observableId, S subscriberId, String topic, Instant now) {
        Objects.requireNonNull(observableId);
        Objects.requireNonNull(subscriberId);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(now);

        ObservableRequest<ID, E> subscriber = subscribers.get(subscriberId);
        if (subscriber == null) {
            return IO.of(Either.left("Subscriber domain object not registered: " + subscriberId));
        }

        // Double dispatch permission check
        Either<String, Void> permCheck = subscriber.checkSubscriptionPermission(topic);
        if (permCheck.isLeft()) {
            return IO.of(Either.left(permCheck.getLeft()));
        }

        return repository.find(observableId)
            .map(opt -> opt.orElseGet(() -> new ObservableLedger<>(observableId)))
            .flatMap(ledger -> {
                Either<String, TransitionResult<ObservableLedger<ID, S>, ObservableEvent<ID, S>>> result = 
                    ledger.subscribe(topic, subscriberId, now);

                if (result.isLeft()) {
                    return IO.of(Either.<String, ObservableLedger<ID, S>>left(result.getLeft()));
                }

                TransitionResult<ObservableLedger<ID, S>, ObservableEvent<ID, S>> transResult = result.getRight();
                ObservableLedger<ID, S> updatedLedger = transResult.aggregate();
                ObservableEvent<ID, S> event = transResult.event();

                // Save, publish and hook physically into broker subscription port
                return repository.save(observableId, updatedLedger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("observable", observableId.toString() + ":subscribe"))
                    .flatMap(v -> subscriberPort.subscribe(topic, rawEvent -> handleIncomingEvent(observableId, subscriberId, topic, rawEvent)))
                    .map(v -> Either.<String, ObservableLedger<ID, S>>right(updatedLedger));
            });
    }

    /**
     * Declarative unsubscription orchestration mapping.
     */
    public IO<Either<String, ObservableLedger<ID, S>>> unsubscribe(ID observableId, S subscriberId, String topic, Instant now) {
        Objects.requireNonNull(observableId);
        Objects.requireNonNull(subscriberId);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(now);

        return repository.find(observableId)
            .flatMap(optLedger -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ObservableLedger<ID, S>>left("Observable ledger not found: " + observableId));
                }

                ObservableLedger<ID, S> ledger = optLedger.get();
                Either<String, TransitionResult<ObservableLedger<ID, S>, ObservableEvent<ID, S>>> result = 
                    ledger.unsubscribe(topic, subscriberId, now);

                if (result.isLeft()) {
                    return IO.of(Either.<String, ObservableLedger<ID, S>>left(result.getLeft()));
                }

                TransitionResult<ObservableLedger<ID, S>, ObservableEvent<ID, S>> transResult = result.getRight();
                ObservableLedger<ID, S> updatedLedger = transResult.aggregate();
                ObservableEvent<ID, S> event = transResult.event();

                return repository.save(observableId, updatedLedger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("observable", observableId.toString() + ":unsubscribe"))
                    .map(v -> Either.<String, ObservableLedger<ID, S>>right(updatedLedger));
            });
    }

    private IO<Void> handleIncomingEvent(ID observableId, S subscriberId, String topic, E rawEvent) {
        return repository.find(observableId)
            .flatMap(optLedger -> {
                if (optLedger.isPresent() && optLedger.get().isSubscribed(topic, subscriberId)) {
                    ObservableRequest<ID, E> subscriber = subscribers.get(subscriberId);
                    if (subscriber != null && subscriber.shouldDeliver(rawEvent)) {
                        return IO.delay(() -> {
                            subscriber.onEventDelivered(rawEvent);
                            return null;
                        }).flatMap(v -> telemetry.recordSuccess("observable", subscriberId.toString() + ":deliver"));
                    }
                }
                return IO.of(null);
            });
    }
}

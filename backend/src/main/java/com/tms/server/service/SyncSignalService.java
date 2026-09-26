package com.tms.server.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canal "temps réel" vers les terminaux (long polling) : chaque agent garde une requête
 * {@code GET /api/device/v1/wait} ouverte ; le serveur y répond immédiatement quand il faut
 * se synchroniser (nouvelle tâche, synchronisation forcée depuis la console).
 * Un signal émis pendant que le terminal n'attend pas est mémorisé jusqu'à sa prochaine attente.
 */
@Service
public class SyncSignalService {

    private static final Map<String, Object> SYNC = Map.of("sync", true);
    private static final Map<String, Object> IDLE = Map.of("sync", false);

    private final Map<Long, List<DeferredResult<Map<String, Object>>>> waiters = new ConcurrentHashMap<>();
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    /** Attente du terminal : répond {"sync":true} dès qu'un signal arrive, {"sync":false} au délai. */
    public DeferredResult<Map<String, Object>> await(Long terminalId, long timeoutMs) {
        DeferredResult<Map<String, Object>> result = new DeferredResult<>(timeoutMs, IDLE);
        if (pending.remove(terminalId)) {
            result.setResult(SYNC);
            return result;
        }
        List<DeferredResult<Map<String, Object>>> list =
                waiters.computeIfAbsent(terminalId, k -> new CopyOnWriteArrayList<>());
        list.add(result);
        Runnable cleanup = () -> list.remove(result);
        result.onCompletion(cleanup);
        result.onTimeout(cleanup);
        result.onError(e -> cleanup.run());
        return result;
    }

    /** Demande au terminal de se synchroniser ; vrai s'il était connecté au canal temps réel. */
    public boolean signal(Long terminalId) {
        List<DeferredResult<Map<String, Object>>> list = waiters.get(terminalId);
        boolean delivered = false;
        if (list != null) {
            for (DeferredResult<Map<String, Object>> r : list) {
                delivered |= r.setResult(SYNC);
            }
        }
        if (!delivered) {
            pending.add(terminalId); // délivré à la prochaine attente du terminal
        }
        return delivered;
    }

    /** Signal émis après validation de la transaction (les nouvelles tâches sont alors visibles). */
    public void signalAfterCommit(Collection<Long> terminalIds) {
        Set<Long> ids = Set.copyOf(terminalIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(SyncSignalService.this::signal);
                }
            });
        } else {
            ids.forEach(this::signal);
        }
    }

    /** Vrai si le terminal a une attente ouverte (canal temps réel actif). */
    public boolean isConnected(Long terminalId) {
        List<DeferredResult<Map<String, Object>>> list = waiters.get(terminalId);
        return list != null && list.stream().anyMatch(r -> !r.isSetOrExpired());
    }
}

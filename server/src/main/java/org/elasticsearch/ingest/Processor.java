/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest;

import org.elasticsearch.client.internal.Client;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.Scheduler;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A processor implementation may modify the data belonging to a document.
 * Whether changes are made and what exactly is modified is up to the implementation.
 *
 * Processors may get called concurrently and thus need to be thread-safe.
 */
public interface Processor {

    /**
     * Introspect and potentially modify the incoming data.
     *
     * Expert method: only override this method if a processor implementation needs to make an asynchronous call,
     * otherwise just overwrite {@link #execute(IngestDocument)}.
     */
    default void execute(IngestDocument ingestDocument, BiConsumer<IngestDocument, Exception> handler) {
        if (isAsync() == false) {
            handler.accept(
                null,
                new UnsupportedOperationException("asynchronous execute method should not be executed for sync processors")
            );
        }
        handler.accept(ingestDocument, null);
    }

    /**
     * Introspect and potentially modify the incoming data.
     *
     * @return If <code>null</code> is returned then the current document will be dropped and not be indexed,
     *         otherwise this document will be kept and indexed
     */
    default IngestDocument execute(IngestDocument ingestDocument) throws Exception {
        if (isAsync()) {
            throw new UnsupportedOperationException("synchronous execute method should not be executed for async processors");
        }
        return ingestDocument;
    }

    /**
     * Gets the type of a processor
     */
    String getType();

    /**
     * Gets the tag of a processor.
     */
    String getTag();

    /**
     * Gets the description of a processor.
     */
    String getDescription();

    default boolean isAsync() {
        return false;
    }

    /**
     * A factory that knows how to construct a processor based on a map of maps.
     */
    interface Factory {

        /**
         * Creates a processor based on the specified map of maps config.
         *  @param processorFactories Other processors which may be created inside this processor
         * @param tag The tag for the processor
         * @param description A short description of what this processor does
         * @param config The configuration for the processor
         *
         * <b>Note:</b> Implementations are responsible for removing the used configuration keys, so that after
         */
        Processor create(Map<String, Factory> processorFactories, String tag, String description, Map<String, Object> config)
            throws Exception;
    }

    /**
     * Infrastructure class that holds services that can be used by processor factories to create processor instances
     * and that gets passed around to all {@link org.elasticsearch.plugins.IngestPlugin}s.
     */
    class Parameters {

        /**
         * Useful to provide access to the node's environment like config directory to processor factories.
         */
        public final Environment env;

        /**
         * Provides processors script support.
         */
        public final ScriptService scriptService;

        /**
         * Provide analyzer support
         */
        public final AnalysisRegistry analysisRegistry;

        /**
         * Allows processors to read headers set by {@link org.elasticsearch.action.support.ActionFilter}
         * instances that have run prior to in ingest.
         */
        public final ThreadContext threadContext;

        public final LongSupplier relativeTimeSupplier;

        public final IngestService ingestService;

        public final Consumer<Runnable> genericExecutor;

        /**
         * Provides scheduler support
         */
        public final BiFunction<Long, Runnable, Scheduler.ScheduledCancellable> scheduler;

        /**
         * Provides access to the node client
         */
        public final Client client;

        public Parameters(
            Environment env,
            ScriptService scriptService,
            AnalysisRegistry analysisRegistry,
            ThreadContext threadContext,
            LongSupplier relativeTimeSupplier,
            BiFunction<Long, Runnable, Scheduler.ScheduledCancellable> scheduler,
            IngestService ingestService,
            Client client,
            Consumer<Runnable> genericExecutor
        ) {
            this.env = env;
            this.scriptService = scriptService;
            this.threadContext = threadContext;
            this.analysisRegistry = analysisRegistry;
            this.relativeTimeSupplier = relativeTimeSupplier;
            this.scheduler = scheduler;
            this.ingestService = ingestService;
            this.client = client;
            this.genericExecutor = genericExecutor;
        }

    }
}

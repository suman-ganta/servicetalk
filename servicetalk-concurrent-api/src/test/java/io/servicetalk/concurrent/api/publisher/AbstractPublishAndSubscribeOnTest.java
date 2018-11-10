/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherWithExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.just;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.notNullValue;

public abstract class AbstractPublishAndSubscribeOnTest {

    protected static final int ORIGINAL_SUBSCRIBER_THREAD = 0;
    protected static final int ORIGINAL_SUBSCRIPTION_THREAD = 1;
    protected static final int OFFLOADED_SUBSCRIBER_THREAD = 2;
    protected static final int OFFLOADED_SUBSCRIPTION_THREAD = 3;
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule originalSourceExecutorRule = new ExecutorRule();

    protected Thread[] setupAndSubscribe(BiFunction<Publisher<String>, Executor, Publisher<String>> offloadingFunction,
                                         Executor executor)
            throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[4];

        Publisher<String> original = new PublisherWithExecutor<>(originalSourceExecutorRule.getExecutor(),
                just("Hello"))
                .doBeforeNext(__ -> capturedThreads[ORIGINAL_SUBSCRIBER_THREAD] = Thread.currentThread())
                .doBeforeRequest(__ -> capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD] = Thread.currentThread());

        Publisher<String> offloaded = offloadingFunction.apply(original, executor);

        offloaded.doBeforeNext(__ -> capturedThreads[OFFLOADED_SUBSCRIBER_THREAD] = Thread.currentThread())
                .doBeforeRequest(__ -> capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD] = Thread.currentThread())
                .doAfterFinally(allDone::countDown)
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onSubscribe(final Subscription s) {
                        // Do not request from the caller thread to make sure synchronous request-onNext does not
                        // pollute thread capturing of subscription.
                        executor.execute(() -> s.request(MAX_VALUE));
                    }

                    @Override
                    public void onNext(final String s) {
                        // noop
                    }

                    @Override
                    public void onError(final Throwable t) {
                        // noop
                    }

                    @Override
                    public void onComplete() {
                        // noop
                    }
                });
        allDone.await();

        assertThat("All threads were not captured.", capturedThreads, hasItemInArray(notNullValue()));

        return capturedThreads;
    }
}
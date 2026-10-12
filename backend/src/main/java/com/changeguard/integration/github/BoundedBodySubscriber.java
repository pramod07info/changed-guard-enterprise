package com.changeguard.integration.github;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import com.changeguard.exception.GitHubIntegrationException;
import org.springframework.http.HttpStatus;

/** Bounds JSON/base64 response memory before parsing any upstream payload. */
final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;
    BoundedBodySubscriber(int limit) { this.limit = limit; }
    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription; subscription.request(1);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        for (var buffer : buffers) {
            if (buffer.remaining() > limit - bytes.size()) {
                subscription.cancel();
                result.completeExceptionally(new GitHubIntegrationException(HttpStatus.BAD_GATEWAY, "GitHub response exceeds the supported size"));
                return;
            }
            byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
    @Override public void onComplete() { result.complete(bytes.toByteArray()); }
}

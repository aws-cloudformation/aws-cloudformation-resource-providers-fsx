package software.amazon.fsx.datarepositoryassociation;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.DelayFactory;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.delay.Constant;

public class AbstractTestBase {
    public AbstractTestBase() {

    }

    public static final DelayFactory FAST_DELAY_FACTORY = (apiCall, incoming) -> incoming != null
            ? incoming
            : Constant.of().delay(Duration.ofMillis(1)).timeout(Duration.ofMinutes(1)).build();

    protected static final Credentials MOCK_CREDENTIALS;
    protected static final LoggerProxy logger;

    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();
    }

    ProxyClient<FSxClient> mockProxy(
            final AmazonWebServicesClientProxy proxy,
            final FSxClient sdkClient) {
        return new ProxyClient<FSxClient>() {
            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
                injectCredentialsAndInvokeV2(final RequestT request,
                                         final Function<RequestT, ResponseT> requestFunction) {
                return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> CompletableFuture<ResponseT>
                injectCredentialsAndInvokeV2Async(final RequestT request,
                                              final Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>> IterableT
                injectCredentialsAndInvokeIterableV2(final RequestT request,
                                                           final Function<RequestT, IterableT> requestFunction) {
                return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
                injectCredentialsAndInvokeV2InputStream(final RequestT requestT,
                                                        final Function<RequestT, ResponseInputStream<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
                injectCredentialsAndInvokeV2Bytes(final RequestT requestT,
                                                  final Function<RequestT, ResponseBytes<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FSxClient client() {
                return sdkClient;
            }
        };
    }
}

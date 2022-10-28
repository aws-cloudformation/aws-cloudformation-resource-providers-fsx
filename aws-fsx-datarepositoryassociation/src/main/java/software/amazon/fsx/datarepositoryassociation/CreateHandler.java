package software.amazon.fsx.datarepositoryassociation;

import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.fsx.common.handler.Tagging;

import java.time.Duration;

public class CreateHandler extends BaseHandlerStd {
    private Logger logger;
    private static final long DEFAULT_DELAY_IN_SECONDS = 5L;
    private static final int DEFAULT_TIMEOUT_FOR_DRA_IN_MINUTES = 120;
    private static final Constant BACKOFF_DELAY =
            Constant.of()
                    //Default from {@link software.amazon.cloudformation.proxy.DelayFactory#CONSTANT_DEFAULT_DELAY_FACTORY}.
                    .delay(Duration.ofSeconds(DEFAULT_DELAY_IN_SECONDS))
                    // The timeout can be large because DRAs are run sequentially. Reflect this in stabilization
                    .timeout(Duration.ofMinutes(DEFAULT_TIMEOUT_FOR_DRA_IN_MINUTES))
                    .build();

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<FSxClient> proxyClient,
        final Logger logger) {

        this.logger = logger;


        //Check that tags are all valid
        Tagging.validateTags(Translator.translateTagsToSdk(request.getDesiredResourceState().getTags()));

        // Merge all tags together.
        final Tagging.TagSet allTags = Tagging.TagSet.builder()
                .resourceTags(Translator.translateTagsToSdk(request.getDesiredResourceState().getTags()))
                .stackTags(Tagging.translateTagsMapToSdk(request.getDesiredResourceTags()))
                .systemTags(Tagging.translateTagsMapToSdk(request.getSystemTags()))
                .build();

        // Create data repository association.
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-FSx-DataRepositoryAssociation::Create",
                                proxyClient,
                                progress.getResourceModel(),
                                progress.getCallbackContext())
                        .translateToServiceRequest(model ->
                                Translator.translateToCreateRequest(model,
                                        allTags,
                                        request.getClientRequestToken()))
                        .backoffDelay(BACKOFF_DELAY)
                        .makeServiceCall((awsRequest, client) ->
                                DataRepositoryAssociationUtils.createDataRepositoryAssociation(logger,
                                        awsRequest,
                                        client,
                                        request.getDesiredResourceState()))
                        .stabilize((awsRequest, awsResponse, client, model, context) ->
                                DataRepositoryAssociationUtils.isDRALifecycleAvailable(logger, client, model))
                        .handleError((awsRequest, exception, client, model, context) ->
                                DataRepositoryAssociationUtils.handleError(exception))
                        .progress()
                )
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}

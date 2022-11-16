package software.amazon.fsx.datarepositoryassociation;

import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<FSxClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        return proxy.initiate("AWS-FSx-DataRepositoryAssociation::Read",
                        proxyClient,
                        request.getDesiredResourceState(),
                        callbackContext)
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall((awsRequest, client) -> {
                    final DescribeDataRepositoryAssociationsResponse awsResponse =
                            DataRepositoryAssociationUtils.describeDeletedDRAAndThrowResourceDNE(logger,
                                    awsRequest,
                                    client);
                    logger.log(String.format("%s [%s] has successfully been read.",
                            ResourceModel.TYPE_NAME,
                            awsResponse.associations().get(0)));
                    return awsResponse;
                })
                .handleError((awsRequest, exception, client, model, context) ->
                        DataRepositoryAssociationUtils.handleError(exception))
                .done(awsResponse ->
                        ProgressEvent.defaultSuccessHandler(Translator.translateFromReadResponse(awsResponse,
                                request.getDesiredResourceState().getAssociationId())));
    }
}

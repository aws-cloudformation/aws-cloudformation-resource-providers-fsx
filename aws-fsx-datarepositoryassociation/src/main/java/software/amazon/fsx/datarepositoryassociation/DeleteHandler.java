package software.amazon.fsx.datarepositoryassociation;

import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.cloudwatch.model.InvalidParameterValueException;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociationNotFoundException;
import software.amazon.awssdk.services.fsx.model.DeleteDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.DeleteDataRepositoryAssociationResponse;
import software.amazon.awssdk.services.fsx.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<FSxClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        if (StringUtils.isEmpty(request.getDesiredResourceState().getAssociationId())) {
            throw new CfnNotFoundException(InvalidParameterValueException.builder()
                    .message("Parameter 'AssociationId' must be provided.")
                    .build());
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)

                // Check if resource already does not exist and exit quickly.
                .then(progress ->
                        proxy.initiate("AWS-FSx-DataRepositoryAssociation::Delete::PreDeletionCheck",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToReadRequest)
                                .makeServiceCall((awsRequest, client) ->
                                        DataRepositoryAssociationUtils.describeDeletedDRAAndThrowResourceDNE(logger,
                                                awsRequest,
                                                client))
                                .handleError((awsRequest, exception, client, model, context) ->
                                        DataRepositoryAssociationUtils.handleError(exception))
                                .progress()
                )

                // Delete and wait for resource to no longer exist
                .then(progress ->
                        proxy.initiate("AWS-FSx-DataRepositoryAssociation::Delete",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(model ->
                                        Translator.translateToDeleteRequest(model, request.getClientRequestToken()))
                                .makeServiceCall(this::deleteDataRepositoryAssociation)
                                .stabilize((awsRequest, awsResponse, client, model, context) -> {
                                    boolean stabilized = false;
                                    try {
                                        DataRepositoryAssociationUtils.describeDeletedDRAAndThrowResourceDNE(logger,
                                                Translator.translateToReadRequest(model),
                                                client);
                                    // If we couldn't find the DRA, we are all set, else throw the exception.
                                    } catch (final Exception exception) {
                                        if (exception instanceof ResourceNotFoundException
                                                || exception instanceof DataRepositoryAssociationNotFoundException) {
                                            stabilized = true;
                                        } else {
                                            throw exception;
                                        }
                                    }

                                    logger.log(String.format("%s [%s] deletion has stabilized: %s",
                                            ResourceModel.TYPE_NAME,
                                            model.getPrimaryIdentifier(),
                                            stabilized));
                                    return stabilized;
                                })
                                .handleError((awsRequest, exception, client, model, context) ->
                                        DataRepositoryAssociationUtils.handleError(exception))
                                .progress()
                )
                .then(progress -> ProgressEvent.defaultSuccessHandler(null));
    }

    /**
     * Deletes the DRA.
     * @param deleteRequest The request to delete the DRA.
     * @param client The client to make FSx API calls through.
     * @return The delete response.
     */
    private DeleteDataRepositoryAssociationResponse deleteDataRepositoryAssociation(
            final DeleteDataRepositoryAssociationRequest deleteRequest,
            final ProxyClient<FSxClient> client) {
        final DeleteDataRepositoryAssociationResponse awsResponse =
                client.injectCredentialsAndInvokeV2(deleteRequest, client.client()::deleteDataRepositoryAssociation);

        logger.log(String.format("%s [%s] successfully deleted.",
                ResourceModel.TYPE_NAME,
                deleteRequest.associationId()));
        return awsResponse;
    }
}

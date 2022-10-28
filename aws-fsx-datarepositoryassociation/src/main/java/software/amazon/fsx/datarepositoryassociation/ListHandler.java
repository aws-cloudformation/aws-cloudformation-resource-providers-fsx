package software.amazon.fsx.datarepositoryassociation;

import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;

public class ListHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final DescribeDataRepositoryAssociationsRequest describeRequest =
                Translator.translateToListRequest(request.getNextToken());
        final DescribeDataRepositoryAssociationsResponse describeResponse =
                proxy.injectCredentialsAndInvokeV2(describeRequest,
                        ClientBuilder.getClient()::describeDataRepositoryAssociations);

        final String nextToken = describeResponse.nextToken();

        final List<ResourceModel> models = Translator.translateFromListRequest(describeResponse);

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(models)
                .nextToken(nextToken)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}

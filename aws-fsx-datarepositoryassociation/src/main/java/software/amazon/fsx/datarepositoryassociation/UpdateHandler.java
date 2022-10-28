package software.amazon.fsx.datarepositoryassociation;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.cloudwatch.model.InvalidParameterValueException;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DataRepositoryLifecycle;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.TagResourceResponse;
import software.amazon.awssdk.services.fsx.model.UntagResourceResponse;
import software.amazon.awssdk.services.fsx.model.UpdateDataRepositoryAssociationResponse;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.fsx.common.handler.Tagging;

import java.util.Map;
import java.util.Set;

public class UpdateHandler extends BaseHandlerStd {

    public static final Set<DataRepositoryLifecycle> UPDATE_AVAILABLE_LIFECYCLES =
            ImmutableSet.of(DataRepositoryLifecycle.AVAILABLE, DataRepositoryLifecycle.MISCONFIGURED);
    public static final Set<DataRepositoryLifecycle> UPDATE_FAILED_LIFECYCLES = ImmutableSet.of(DataRepositoryLifecycle.FAILED);

    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<FSxClient> proxyClient,
            final Logger logger) {

        this.logger = logger;
        final Map<String, String> desiredTags = TagHelper.getNewDesiredTags(request.getDesiredResourceState(), request);
        final Map<String, String> previousTags = TagHelper.getPreviouslyAttachedTags(request);
        final ResourceModel newModel = request.getDesiredResourceState();
        final ResourceModel oldModel = request.getPreviousResourceState() == null
                ? request.getDesiredResourceState()
                : request.getPreviousResourceState();

        DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(newModel, oldModel);

        if (StringUtils.isEmpty(newModel.getAssociationId())) {
            throw new CfnNotFoundException(InvalidParameterValueException.builder()
                    .message("Parameter 'AssociationId' must be provided.")
                    .build());
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)

                // If resource does not exist, fail the request.
                .then(progress ->
                        proxy.initiate("AWS-FSx-DataRepositoryAssociation::Update::PreUpdateCheck",
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

                // TODO [Rishav Bose 2022-06-13] Merge into one update once each property can be updated in one API call
                //  without the lifecycle changing to AVAILABLE in the middle.

                // Update chunk size.
                .then(progress -> {
                    if (Translator.shouldUpdateImportedFileChunkSize(newModel.getImportedFileChunkSize(),
                            oldModel.getImportedFileChunkSize())) {
                        return proxy.initiate("AWS-FSx-DataRepositoryAssociation::Update::ChunkSize",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToUpdateImportedFileChunkSize)
                                .makeServiceCall((awsRequest, client) -> {
                                    final ResourceModel model = progress.getResourceModel();
                                    final DescribeDataRepositoryAssociationsResponse describeResponse =
                                            client.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model),
                                                    client.client()::describeDataRepositoryAssociations);
                                    final DataRepositoryAssociation association =
                                            DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);
                                    //If the value is already up-to-date, exit early.
                                    if (!Translator.shouldUpdateImportedFileChunkSize(association.importedFileChunkSize(),
                                            model.getImportedFileChunkSize())) {
                                        return null;
                                    }

                                    final UpdateDataRepositoryAssociationResponse updateResponse =
                                            client.injectCredentialsAndInvokeV2(awsRequest,
                                                    client.client()::updateDataRepositoryAssociation);

                                    logger.log(String.format("%s [%s], property 'ImportedFileChunkSize' has "
                                                    + "successfully been updated.",
                                            ResourceModel.TYPE_NAME,
                                            awsRequest.associationId()));
                                    return updateResponse;
                                })
                                .stabilize((awsRequest, awsResponse, client, model, context) ->
                                        DataRepositoryAssociationUtils.isDRALifecycleAvailable(logger,
                                                client,
                                                model,
                                                UPDATE_AVAILABLE_LIFECYCLES,
                                                UPDATE_FAILED_LIFECYCLES))
                                .progress();
                    } else {
                        return progress;
                    }
                })

                // Update AutoImport.
                .then(progress -> {
                    if (Translator.shouldUpdateS3ImportPolicy(newModel.getS3(), oldModel.getS3())) {
                        return proxy.initiate("AWS-FSx-DataRepositoryAssociation::Update::S3AutoImport",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(Translator::updateS3ImportPolicy)
                                .makeServiceCall((awsRequest, client) -> {
                                    final ResourceModel model = progress.getResourceModel();
                                    final DescribeDataRepositoryAssociationsResponse describeResponse =
                                            client.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model),
                                                    client.client()::describeDataRepositoryAssociations);
                                    final DataRepositoryAssociation association =
                                            DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);
                                    final S3 modelS3 = Translator.convertS3SDKToModel(association);

                                    //If the value is already up-to-date, exit early.
                                    if (!Translator.shouldUpdateS3ImportPolicy(modelS3, model.getS3())) {
                                        return null;
                                    }

                                    final UpdateDataRepositoryAssociationResponse awsResponse =
                                            client.injectCredentialsAndInvokeV2(awsRequest,
                                                    client.client()::updateDataRepositoryAssociation);

                                    logger.log(String.format("%s [%s], property 'AutoImportPolicy' has successfully been updated.",
                                            ResourceModel.TYPE_NAME,
                                            awsRequest.associationId()));
                                    return awsResponse;
                                })
                                .stabilize((awsRequest, awsResponse, client, model, context) ->
                                        DataRepositoryAssociationUtils.isDRALifecycleAvailable(logger,
                                                client,
                                                model,
                                                UPDATE_AVAILABLE_LIFECYCLES,
                                                UPDATE_FAILED_LIFECYCLES))
                                .progress();
                    } else {
                        return progress;
                    }
                })

                // Update AutoExport.
                .then(progress -> {
                    if (Translator.shouldUpdateS3ExportPolicy(newModel.getS3(), oldModel.getS3())) {
                        return proxy.initiate("AWS-FSx-DataRepositoryAssociation::Update::S3AutoExport",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(Translator::updateS3ExportPolicy)
                                .makeServiceCall((awsRequest, client) -> {
                                    final ResourceModel model = progress.getResourceModel();
                                    final DescribeDataRepositoryAssociationsResponse describeResponse =
                                            client.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model),
                                                    client.client()::describeDataRepositoryAssociations);
                                    final DataRepositoryAssociation association =
                                            DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);
                                    final S3 modelS3 = Translator.convertS3SDKToModel(association);

                                    //If the value is already up-to-date, exit early.
                                    if (!Translator.shouldUpdateS3ExportPolicy(modelS3, model.getS3())) {
                                        return null;
                                    }

                                    final UpdateDataRepositoryAssociationResponse awsResponse =
                                            client.injectCredentialsAndInvokeV2(awsRequest,
                                                    client.client()::updateDataRepositoryAssociation);

                                    logger.log(String.format("%s [%s], property 'AutoExportPolicy' has successfully been updated.",
                                            ResourceModel.TYPE_NAME,
                                            awsRequest.associationId()));
                                    return awsResponse;
                                })
                                .stabilize((awsRequest, awsResponse, client, model, context) ->
                                        DataRepositoryAssociationUtils.isDRALifecycleAvailable(logger,
                                                client,
                                                model,
                                                UPDATE_AVAILABLE_LIFECYCLES,
                                                UPDATE_FAILED_LIFECYCLES))
                                .progress();
                    } else {
                        return progress;
                    }
                })

                //Remove old tags
                .then(progress -> {
                    final Set<String> tagsToRemove = Tagging.generateTagsToRemove(previousTags, desiredTags);
                    if (!tagsToRemove.isEmpty()) {
                        return proxy.initiate("AWS-FSx-DataRepositoryAssociation::Update::removeTags",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(model -> {
                                    final DescribeDataRepositoryAssociationsResponse describeResponse =
                                            proxyClient.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model),
                                                    proxyClient.client()::describeDataRepositoryAssociations);
                                    final DataRepositoryAssociation association =
                                            DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

                                    return Translator.translateToUntagResourceRequest(association, tagsToRemove);
                                })
                                .makeServiceCall((awsRequest, client) -> {
                                    final UntagResourceResponse awsResponse =
                                            client.injectCredentialsAndInvokeV2(awsRequest, client.client()::untagResource);

                                    logger.log(String.format("%s [%s], updated to remove old tags.",
                                            ResourceModel.TYPE_NAME,
                                            progress.getResourceModel().getAssociationId()));
                                    return awsResponse;
                                })
                                .progress();
                    } else {
                        return progress;
                    }
                })
                // Add new tags.
                .then(progress -> {
                    final Map<String, String> tagsToAdd = Tagging.generateTagsToAdd(previousTags, desiredTags);
                    if (!tagsToAdd.isEmpty()) {

                        //Check that tags are all valid
                        Tagging.validateTags(Tagging.getAllNonCloudFormationAwsPrefixedKeys(
                                Tagging.translateTagsMapToSdk(tagsToAdd)));

                        return proxy.initiate("AWS-FSx-DataRepositoryAssociation::Update::addTags",
                                        proxyClient,
                                        progress.getResourceModel(),
                                        progress.getCallbackContext())
                                .translateToServiceRequest(model -> {
                                    final DescribeDataRepositoryAssociationsResponse describeResponse =
                                            proxyClient.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model),
                                                    proxyClient.client()::describeDataRepositoryAssociations);
                                    final DataRepositoryAssociation association =
                                            DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

                                    return Translator.translateToTagResourceRequest(association, tagsToAdd);
                                })
                                .makeServiceCall((awsRequest, client) -> {
                                    final TagResourceResponse awsResponse =
                                            client.injectCredentialsAndInvokeV2(awsRequest, client.client()::tagResource);

                                    logger.log(String.format("%s [%s], updated to add new tags.",
                                            ResourceModel.TYPE_NAME,
                                            progress.getResourceModel().getAssociationId()));
                                    return awsResponse;
                                })
                                .progress();
                    } else {
                        return progress;
                    }
                })

                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}

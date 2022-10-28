package software.amazon.fsx.datarepositoryassociation;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.cloudwatch.model.InvalidParameterValueException;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.BadRequestException;
import software.amazon.awssdk.services.fsx.model.CreateDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.CreateDataRepositoryAssociationResponse;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociationNotFoundException;
import software.amazon.awssdk.services.fsx.model.DataRepositoryLifecycle;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.FileSystemNotFoundException;
import software.amazon.awssdk.services.fsx.model.IncompatibleParameterErrorException;
import software.amazon.awssdk.services.fsx.model.InternalServerErrorException;
import software.amazon.awssdk.services.fsx.model.InvalidDataRepositoryTypeException;
import software.amazon.awssdk.services.fsx.model.ResourceNotFoundException;
import software.amazon.awssdk.services.fsx.model.ServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DataRepositoryAssociationUtils {

    public static final Set<DataRepositoryLifecycle> DEFAULT_AVAILABLE_LIFECYCLES =
            ImmutableSet.of(DataRepositoryLifecycle.AVAILABLE);
    public static final Set<DataRepositoryLifecycle> DEFAULT_FAILED_LIFECYCLES =
            ImmutableSet.of(DataRepositoryLifecycle.MISCONFIGURED,
                    DataRepositoryLifecycle.FAILED);

    static final Map<Class<?>, HandlerErrorCode> EXCEPTION_TO_ERROR_CODE = new HashMap<>();

    static {
        EXCEPTION_TO_ERROR_CODE.put(FileSystemNotFoundException.class, HandlerErrorCode.NotFound);
        EXCEPTION_TO_ERROR_CODE.put(ResourceNotFoundException.class, HandlerErrorCode.NotFound);
        EXCEPTION_TO_ERROR_CODE.put(DataRepositoryAssociationNotFoundException.class, HandlerErrorCode.NotFound);
        EXCEPTION_TO_ERROR_CODE.put(BadRequestException.class, HandlerErrorCode.InvalidRequest);
        EXCEPTION_TO_ERROR_CODE.put(IncompatibleParameterErrorException.class, HandlerErrorCode.InvalidRequest);
        EXCEPTION_TO_ERROR_CODE.put(InvalidDataRepositoryTypeException.class, HandlerErrorCode.InvalidRequest);
        EXCEPTION_TO_ERROR_CODE.put(InternalServerErrorException.class, HandlerErrorCode.InternalFailure);
        EXCEPTION_TO_ERROR_CODE.put(ServiceLimitExceededException.class, HandlerErrorCode.ServiceLimitExceeded);
    }

    private DataRepositoryAssociationUtils() {
    }

    /**
     * Takes FSx errors for DRAs and translates to a ProgressEvent if applicable.
     * @param exception The exception to make a decision against.
     * @return The ProgressEvent, which determines the next step of the handler.
     * @throws Exception Re-throws the exception if we have no logic for it.
     */
    static ProgressEvent<ResourceModel, CallbackContext> handleError(final Exception exception) throws Exception {
        if (EXCEPTION_TO_ERROR_CODE.containsKey(exception.getClass())) {
            return ProgressEvent.defaultFailureHandler(exception, EXCEPTION_TO_ERROR_CODE.get(exception.getClass()));
        }
        throw exception;
    }

    /**
     * Describes the data repository association and, if we can't find it, throws a ResourceNotFoundException to handle
     *  later.
     * @param logger The logger to use to log messages.
     * @param describeRequest The DescribeDataRepositoryAssociationsRequest created from the translator.
     * @param client The client to call APIs through.
     * @return The describe response.
     */
    static DescribeDataRepositoryAssociationsResponse describeDeletedDRAAndThrowResourceDNE(
            final Logger logger,
            final DescribeDataRepositoryAssociationsRequest describeRequest,
            final ProxyClient<FSxClient> client) {
        final DescribeDataRepositoryAssociationsResponse describeResponse =
                client.injectCredentialsAndInvokeV2(describeRequest, client.client()::describeDataRepositoryAssociations);
        if (describeResponse == null || CollectionUtils.isEmpty(describeResponse.associations())) {
            throw ResourceNotFoundException.builder()
                    .message(String.format("Data repository association does not exist for: %s.",
                            describeRequest.associationIds().get(0)))
                    .build();
        }

        logger.log(String.format("%s [%s] successfully read.",
                ResourceModel.TYPE_NAME,
                describeResponse.associations().get(0)));
        return describeResponse;
    }



    /**
     * Determines if the data repository association lifecycle is in one of the default available states or throws and
     *  exception if it is failed.
     * @param logger The logger to use to log messages.
     * @param client The client to call APIs through.
     * @param model The model the function is based on.
     * @return If the DRA is in one of the default available states.
     */
    static boolean isDRALifecycleAvailable(final Logger logger,
                                           final ProxyClient<FSxClient> client,
                                           final ResourceModel model) {
        return isDRALifecycleAvailable(logger, client, model, DEFAULT_AVAILABLE_LIFECYCLES, DEFAULT_FAILED_LIFECYCLES);
    }

    /**
     * Determines if the data repository association lifecycle is in one of the default available states or throws and
     *  exception if it is failed.
     * @param logger The logger to use to log messages.
     * @param client The client to call APIs through.
     * @param model The model the function is based on.
     * @param availableLifecycles The lifecycles to consider available.
     * @param failedLifecycles The lifecycles to consider failed.
     * @return If the DRA is in one of the default available states.
     */
    static boolean isDRALifecycleAvailable(final Logger logger,
                                           final ProxyClient<FSxClient> client,
                                           final ResourceModel model,
                                           final Set<DataRepositoryLifecycle> availableLifecycles,
                                           final Set<DataRepositoryLifecycle> failedLifecycles) {
        boolean stabilized = false;

        final DescribeDataRepositoryAssociationsRequest describeRequest = DescribeDataRepositoryAssociationsRequest.builder()
                .associationIds(model.getAssociationId())
                .build();
        final DescribeDataRepositoryAssociationsResponse describeResponse =
                client.injectCredentialsAndInvokeV2(describeRequest, client.client()::describeDataRepositoryAssociations);

        final DataRepositoryAssociation association = getDRAFromDescribeResponse(describeResponse);
        if (association != null) {
            if (availableLifecycles.contains(association.lifecycle())) {
                stabilized = true;
            } else if (failedLifecycles.contains(association.lifecycle())) {
                logger.log(String.format("Data repository association (%s) for file system (%s) is in a failed state "
                                + "[%s] with failure message: %s",
                        association.associationId(),
                        association.fileSystemId(),
                        association.lifecycle(),
                        association.failureDetails()));
                throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getAssociationId());
            }
        }

        logger.log(String.format("%s [%s] has stabilized: %s",
                ResourceModel.TYPE_NAME,
                model.getPrimaryIdentifier(),
                stabilized));
        return stabilized;
    }

    /**
     * Extract the DRA from the DescribeDRA response.
     * @param describeResponse The describe response.
     * @return The DRA if it exists.
     */
    static DataRepositoryAssociation getDRAFromDescribeResponse(final DescribeDataRepositoryAssociationsResponse describeResponse) {
        DataRepositoryAssociation association = null;
        if (describeResponse != null && !CollectionUtils.isEmpty(describeResponse.associations())
                && describeResponse.associations().size() == 1
                && !describeResponse.associations().get(0).lifecycle().equals(DataRepositoryLifecycle.UNKNOWN_TO_SDK_VERSION)) {
            association = describeResponse.associations().get(0);
        }
        return association;
    }

    /**
     * Create data repository association based on model.
     * @param createRequest CreateDataRepositoryAssociationRequest created from the translator.
     * @param client The client to call APIs through.
     * @param model The model for the request.
     * @return The create response.
     */
    static CreateDataRepositoryAssociationResponse createDataRepositoryAssociation(final Logger logger,
            final CreateDataRepositoryAssociationRequest createRequest,
            final ProxyClient<FSxClient> client,
            final ResourceModel model) {
        final CreateDataRepositoryAssociationResponse createResponse =
                client.injectCredentialsAndInvokeV2(createRequest, client.client()::createDataRepositoryAssociation);

        //Set data repository association id so that it can be used in the stabilizer and read handler.
        if (StringUtils.isEmpty(model.getAssociationId())) {
            model.setAssociationId(createResponse.association().associationId());
        }

        logger.log(String.format("%s [%s] successfully created.",
                ResourceModel.TYPE_NAME,
                createResponse.association().associationId()));
        return createResponse;
    }

    /**
     * Makes sure immutable properties are not being updated.
     * @param newModel The model we update to.
     * @param prevModel The model we update from.
     */
    static void validatePropertiesAreUpdatable(final ResourceModel newModel,
                                                final ResourceModel prevModel) {
        CfnNotUpdatableException exception = null;
        if (StringUtils.isNotEmpty(prevModel.getResourceARN())
                && !StringUtils.equals(newModel.getResourceARN(), prevModel.getResourceARN())) {
            exception = throwCfnNotUpdatableException("ResourceARN");
        } else if (!StringUtils.equals(newModel.getFileSystemId(), prevModel.getFileSystemId())) {
            exception = throwCfnNotUpdatableException("FileSystemId");
        } else if (!StringUtils.equals(newModel.getFileSystemPath(), prevModel.getFileSystemPath())) {
            exception = throwCfnNotUpdatableException("FileSystemPath");
        } else if (!StringUtils.equals(newModel.getDataRepositoryPath(), prevModel.getDataRepositoryPath())) {
            exception = throwCfnNotUpdatableException("DataRepositoryPath");
        } else if (!Objects.equals(newModel.getBatchImportMetaDataOnCreate(), prevModel.getBatchImportMetaDataOnCreate())) {
            exception = throwCfnNotUpdatableException("BatchImportMetaDataOnCreate");
        }

        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Creates and throws a throwCfnNotUpdatableException for cases where immutable properties are updated.
     * @param propertyName The immutable property that failed to be updated.
     */
    private static CfnNotUpdatableException throwCfnNotUpdatableException(final String propertyName) {
        return new CfnNotUpdatableException(InvalidParameterValueException.builder()
                .message(String.format("Parameter '%s' is not updatable.", propertyName))
                .build());
    }
}

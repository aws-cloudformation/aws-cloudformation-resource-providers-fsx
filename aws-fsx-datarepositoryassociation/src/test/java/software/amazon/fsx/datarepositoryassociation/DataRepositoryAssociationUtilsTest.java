package software.amazon.fsx.datarepositoryassociation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static software.amazon.fsx.datarepositoryassociation.DataRepositoryAssociationUtils.DEFAULT_AVAILABLE_LIFECYCLES;
import static software.amazon.fsx.datarepositoryassociation.DataRepositoryAssociationUtils.DEFAULT_FAILED_LIFECYCLES;
import static software.amazon.fsx.datarepositoryassociation.DataRepositoryAssociationUtils.EXCEPTION_TO_ERROR_CODE;

@ExtendWith(MockitoExtension.class)
public class DataRepositoryAssociationUtilsTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<FSxClient> proxyClient;

    @Mock
    FSxClient fsxClient;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger,
            MOCK_CREDENTIALS,
            () -> Duration.ofSeconds(600).toMillis(),
            FAST_DELAY_FACTORY);
        fsxClient = mock(FSxClient.class);
        proxyClient = mockProxy(proxy, fsxClient);
    }

    @AfterEach
    public void tear_down() {
        verifyNoMoreInteractions(fsxClient);
    }

    @Test
    public void testHandleError() throws Exception {
        final Map<Class<?>, Exception> classToExceptionExample = new HashMap<>();
        classToExceptionExample.put(FileSystemNotFoundException.class,
                FileSystemNotFoundException.builder().build());
        classToExceptionExample.put(ResourceNotFoundException.class,
                ResourceNotFoundException.builder().build());
        classToExceptionExample.put(DataRepositoryAssociationNotFoundException.class,
                DataRepositoryAssociationNotFoundException.builder().build());
        classToExceptionExample.put(BadRequestException.class,
                BadRequestException.builder().build());
        classToExceptionExample.put(IncompatibleParameterErrorException.class,
                IncompatibleParameterErrorException.builder().build());
        classToExceptionExample.put(InvalidDataRepositoryTypeException.class,
                InvalidDataRepositoryTypeException.builder().build());
        classToExceptionExample.put(InternalServerErrorException.class,
                InternalServerErrorException.builder().build());

        for (Map.Entry<Class<?>, Exception> entry: classToExceptionExample.entrySet()) {
            final ProgressEvent<ResourceModel, CallbackContext> event =
                    DataRepositoryAssociationUtils.handleError(entry.getValue());
            assertThat(event.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE.get(entry.getKey()));
        }

        final CfnInvalidRequestException unhandledException = new CfnInvalidRequestException(/*requestBody*/ "test");
        assertThatThrownBy(() -> DataRepositoryAssociationUtils.handleError(unhandledException))
                .isEqualTo(unhandledException);
    }

    @Test
    public void testDescribeDeletedDRAAndThrowResourceDNE_HappyPath() {
        final String associationId = "dra-123456789";
        final DescribeDataRepositoryAssociationsRequest describeRequest = DescribeDataRepositoryAssociationsRequest.builder()
                .associationIds(associationId)
                .build();

        final DataRepositoryAssociation associationToReturn = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .build();

        final DescribeDataRepositoryAssociationsResponse expectedDescribeResponse =
                DescribeDataRepositoryAssociationsResponse.builder()
                        .associations(associationToReturn)
                        .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(expectedDescribeResponse);

        final DescribeDataRepositoryAssociationsResponse actualDescribeResponse = DataRepositoryAssociationUtils
                .describeDeletedDRAAndThrowResourceDNE(logger, describeRequest, proxyClient);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        assertThat(expectedDescribeResponse).isEqualTo(actualDescribeResponse);
    }

    @Test
    public void testDescribeDeletedDRAAndThrowResourceDNE_NullResponse() {
        final String associationId = "dra-123456789";
        final DescribeDataRepositoryAssociationsRequest describeRequest = DescribeDataRepositoryAssociationsRequest.builder()
                .associationIds(associationId)
                .build();

        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> DataRepositoryAssociationUtils
                .describeDeletedDRAAndThrowResourceDNE(logger, describeRequest, proxyClient))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));
    }

    @Test
    public void testDescribeDeletedDRAAndThrowResourceDNE_NullAssociations() {
        final String associationId = "dra-123456789";
        final DescribeDataRepositoryAssociationsRequest describeRequest = DescribeDataRepositoryAssociationsRequest.builder()
                .associationIds(associationId)
                .build();

        final DescribeDataRepositoryAssociationsResponse expectedDescribeResponse =
                DescribeDataRepositoryAssociationsResponse.builder().build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(expectedDescribeResponse);

        assertThatThrownBy(() -> DataRepositoryAssociationUtils
                .describeDeletedDRAAndThrowResourceDNE(logger, describeRequest, proxyClient))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));
    }

    @Test
    public void testDescribeDeletedDRAAndThrowResourceDNE_EmptyAssociations() {
        final String associationId = "dra-123456789";
        final DescribeDataRepositoryAssociationsRequest describeRequest = DescribeDataRepositoryAssociationsRequest.builder()
                .associationIds(associationId)
                .build();

        final DescribeDataRepositoryAssociationsResponse expectedDescribeResponse =
                DescribeDataRepositoryAssociationsResponse.builder()
                        .associations(new ArrayList<>())
                        .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(expectedDescribeResponse);

        assertThatThrownBy(() -> DataRepositoryAssociationUtils
                .describeDeletedDRAAndThrowResourceDNE(logger, describeRequest, proxyClient))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));
    }

    @Test
    public void testIsDRALifecycleAvailable_AvailableLifecycle() {
        final DataRepositoryLifecycle lifecycleOfDRA = DataRepositoryLifecycle.MISCONFIGURED;
        final Set<DataRepositoryLifecycle> availableLifecycles = Collections.singleton(lifecycleOfDRA);
        final Set<DataRepositoryLifecycle> failedLifecycles = Collections.singleton(DataRepositoryLifecycle.FAILED);
        testIsDRALifecycleAvailableHelper(lifecycleOfDRA,
                availableLifecycles,
                failedLifecycles);
    }

    @Test
    public void testIsDRALifecycleAvailable_FailedLifecycle() {
        final DataRepositoryLifecycle lifecycleOfDRA = DEFAULT_FAILED_LIFECYCLES.iterator().next();
        testIsDRALifecycleAvailableHelper(lifecycleOfDRA,
                DEFAULT_AVAILABLE_LIFECYCLES,
                DEFAULT_FAILED_LIFECYCLES);
    }

    @Test
    public void testIsDRALifecycleAvailable_NotAvailableLifecycle() {
        final DataRepositoryLifecycle lifecycleOfDRA = DataRepositoryLifecycle.CREATING;
        testIsDRALifecycleAvailableHelper(lifecycleOfDRA,
                DEFAULT_AVAILABLE_LIFECYCLES,
                DEFAULT_FAILED_LIFECYCLES);
    }

    @Test
    public void testIsDRALifecycleAvailable_NullAssociation() {
        final String associationId = "dra-123456789";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(/*describeResponse*/ null);

        final boolean isDRALifecycleAvailable = DataRepositoryAssociationUtils
                .isDRALifecycleAvailable(logger,
                        proxyClient,
                        model);

        assertThat(isDRALifecycleAvailable).isFalse();

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));
    }

    @Test
    public void testGetDRAFromDescribeResponse_HappyPath() {
        final String associationId = "dra-123456789";

        final DataRepositoryAssociation expectedAssociation = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(expectedAssociation)
                .build();

        final DataRepositoryAssociation actualAssociation =
                DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

        assertThat(expectedAssociation).isEqualTo(actualAssociation);
    }

    @Test
    public void testGetDRAFromDescribeResponse_UnknownLifecycle() {
        final String associationId = "dra-123456789";

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.UNKNOWN_TO_SDK_VERSION)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(association)
                .build();

        final DataRepositoryAssociation actualAssociation =
                DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

        assertThat(actualAssociation).isNull();
    }

    @Test
    public void testGetDRAFromDescribeResponse_TwoAssociations() {
        final String associationId1 = "dra-123456789";
        final String associationId2 = "dra-123456788";

        final DataRepositoryAssociation association1 = DataRepositoryAssociation.builder()
                .associationId(associationId1)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DataRepositoryAssociation association2 = DataRepositoryAssociation.builder()
                .associationId(associationId2)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(Arrays.asList(association1, association2))
                .build();

        final DataRepositoryAssociation actualAssociation =
                DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

        assertThat(actualAssociation).isNull();
    }

    @Test
    public void testGetDRAFromDescribeResponse_NoAssociations() {

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(new ArrayList<>())
                .build();

        final DataRepositoryAssociation actualAssociation =
                DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

        assertThat(actualAssociation).isNull();
    }

    @Test
    public void testGetDRAFromDescribeResponse_NullAssociations() {

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .build();

        final DataRepositoryAssociation actualAssociation =
                DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);

        assertThat(actualAssociation).isNull();
    }

    @Test
    public void testGetDRAFromDescribeResponse_NullResponse() {
        final DataRepositoryAssociation actualAssociation =
                DataRepositoryAssociationUtils.getDRAFromDescribeResponse(/*describeResponse*/ null);

        assertThat(actualAssociation).isNull();
    }

    @Test
    public void testCreateDataRepositoryAssociationSetDRAId() {
        final String associationId = "dra-123456789";

        final DataRepositoryAssociation associationToReturn = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .build();

        final ResourceModel model = ResourceModel.builder()
                .build();

        final CreateDataRepositoryAssociationResponse expectedCreateResponse = CreateDataRepositoryAssociationResponse.builder()
                .association(associationToReturn)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.createDataRepositoryAssociation(ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class)))
                .thenReturn(expectedCreateResponse);

        final CreateDataRepositoryAssociationResponse actualDescribeResponse = DataRepositoryAssociationUtils
                .createDataRepositoryAssociation(logger,
                        CreateDataRepositoryAssociationRequest.builder().build(),
                        proxyClient,
                        model);

        verify(fsxClient, atLeastOnce()).createDataRepositoryAssociation(
                ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class));

        assertThat(expectedCreateResponse).isEqualTo(actualDescribeResponse);
        assertThat(model.getAssociationId()).isEqualTo(associationId);
    }

    @Test
    public void testCreateDataRepositoryAssociationDontSetDRAId() {
        final String associationId = "dra-123456789";
        final String associationIdInModel = "dra-1234567";

        final DataRepositoryAssociation associationToReturn = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .build();

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationIdInModel)
                .build();

        final CreateDataRepositoryAssociationResponse expectedCreateResponse = CreateDataRepositoryAssociationResponse.builder()
                .association(associationToReturn)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.createDataRepositoryAssociation(ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class)))
                .thenReturn(expectedCreateResponse);

        final CreateDataRepositoryAssociationResponse actualDescribeResponse = DataRepositoryAssociationUtils
                .createDataRepositoryAssociation(logger,
                        CreateDataRepositoryAssociationRequest.builder().build(),
                        proxyClient,
                        model);

        verify(fsxClient, atLeastOnce()).createDataRepositoryAssociation(
                ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class));

        assertThat(expectedCreateResponse).isEqualTo(actualDescribeResponse);
        assertThat(model.getAssociationId()).isEqualTo(associationIdInModel);
    }

    @Test
    public void testValidatePropertiesAreUpdatable_allCases() {

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456789")
                .resourceARN(/*resourceARN*/ "arn:fs-123456")
                .fileSystemId(/*fileSystemId*/ "fs-123456")
                .fileSystemPath(/*fileSystemPath*/ "/ns1/")
                .dataRepositoryPath(/*dataRepositoryPath*/ "s3://test")
                .batchImportMetaDataOnCreate(/*batchImportMetaDataOnCreate*/ true)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(prevModel.getAssociationId())
                .resourceARN(prevModel.getResourceARN())
                .fileSystemId(prevModel.getFileSystemId())
                .fileSystemPath(prevModel.getFileSystemPath())
                .dataRepositoryPath(prevModel.getDataRepositoryPath())
                .batchImportMetaDataOnCreate(prevModel.getBatchImportMetaDataOnCreate())
                .build();

        //no error
        DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(prevModel, newModel);

        // arn changes
        newModel.setResourceARN(/*resourceARN*/ "new");
        assertThatThrownBy(() -> DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(prevModel, newModel))
                .isInstanceOf(CfnNotUpdatableException.class)
                .hasMessageContaining("ResourceARN");
        newModel.setResourceARN(prevModel.getResourceARN());

        // fsid changes
        newModel.setFileSystemId(/*fileSystemId*/ "new");
        assertThatThrownBy(() -> DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(prevModel, newModel))
                .isInstanceOf(CfnNotUpdatableException.class)
                .hasMessageContaining("FileSystemId");
        newModel.setFileSystemId(prevModel.getFileSystemId());

        // fspath changes
        newModel.setFileSystemPath(/*fileSystemPath*/ "new");
        assertThatThrownBy(() -> DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(prevModel, newModel))
                .isInstanceOf(CfnNotUpdatableException.class)
                .hasMessageContaining("FileSystemPath");
        newModel.setFileSystemPath(prevModel.getFileSystemPath());

        // dr path changes
        newModel.setDataRepositoryPath(/*dataRepositoryPath(*/ "new");
        assertThatThrownBy(() -> DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(prevModel, newModel))
                .isInstanceOf(CfnNotUpdatableException.class)
                .hasMessageContaining("DataRepositoryPath");
        newModel.setDataRepositoryPath(prevModel.getDataRepositoryPath());

        // batchImportMetaDataOnCreate changes
        newModel.setBatchImportMetaDataOnCreate(/*batchImportMetaDataOnCreate*/ false);
        assertThatThrownBy(() -> DataRepositoryAssociationUtils.validatePropertiesAreUpdatable(prevModel, newModel))
                .isInstanceOf(CfnNotUpdatableException.class)
                .hasMessageContaining("BatchImportMetaDataOnCreate");
        newModel.setBatchImportMetaDataOnCreate(prevModel.getBatchImportMetaDataOnCreate());

    }

    private void testIsDRALifecycleAvailableHelper(final DataRepositoryLifecycle lifecycleOfDRA,
                                                   final Set<DataRepositoryLifecycle> availableLifecycles,
                                                   final Set<DataRepositoryLifecycle> failedLifecycles) {
        final String associationId = "dra-123456789";

        final DataRepositoryAssociation associationToReturn = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(lifecycleOfDRA)
                .build();


        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .build();

        final DescribeDataRepositoryAssociationsResponse expectedDescribeResponse =
                DescribeDataRepositoryAssociationsResponse.builder()
                        .associations(associationToReturn)
                        .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(expectedDescribeResponse);

        if (availableLifecycles.contains(lifecycleOfDRA)) {
            final boolean isDRALifecycleAvailable = DataRepositoryAssociationUtils
                    .isDRALifecycleAvailable(logger,
                            proxyClient, model,
                            availableLifecycles,
                            failedLifecycles);

            assertThat(isDRALifecycleAvailable).isTrue();
        } else if (failedLifecycles.contains(lifecycleOfDRA)) {
            assertThatThrownBy(() -> DataRepositoryAssociationUtils
                    .isDRALifecycleAvailable(logger,
                            proxyClient, model,
                            availableLifecycles,
                            failedLifecycles))
                    .isInstanceOf(CfnNotStabilizedException.class);
        } else {

            final boolean isDRALifecycleAvailable = DataRepositoryAssociationUtils
                    .isDRALifecycleAvailable(logger,
                            proxyClient, model,
                            availableLifecycles,
                            failedLifecycles);

            assertThat(isDRALifecycleAvailable).isFalse();
        }

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

    }
}

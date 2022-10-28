package software.amazon.fsx.datarepositoryassociation;

import java.time.Duration;
import java.util.ArrayList;

import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociationNotFoundException;
import software.amazon.awssdk.services.fsx.model.DataRepositoryLifecycle;
import software.amazon.awssdk.services.fsx.model.DeleteDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.DeleteDataRepositoryAssociationResponse;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.InternalServerErrorException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<FSxClient> proxyClient;

    @Mock
    FSxClient fsxClient;

    private boolean checkServiceName;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger,
                MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis(),
                FAST_DELAY_FACTORY);
        fsxClient = mock(FSxClient.class);
        proxyClient = mockProxy(proxy, fsxClient);
        checkServiceName = true;
    }

    @AfterEach
    public void tear_down() {
        if (checkServiceName) {
            verify(fsxClient, atLeastOnce()).serviceName();
        }
        verifyNoMoreInteractions(fsxClient);
    }

    @Test
    public void handleRequest_NoDRAId() {
        this.checkServiceName = false;

        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThatThrownBy(() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnNotFoundException.class);
    }

    @Test
    public void handleRequest_DRANotFound() {
        final DeleteHandler handler = new DeleteHandler();
        final String associationId = "dra-12345678";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .tags(new ArrayList<>())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(DescribeDataRepositoryAssociationsResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_ThrowsUnknownException() {
        final DeleteHandler handler = new DeleteHandler();
        final String associationId = "dra-12345678";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .tags(new ArrayList<>())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(association)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(describeResponse) //First call to see that resource exists
                .thenThrow(InternalServerErrorException.builder().build()); //Second stabilize throws exception to exit

        when(fsxClient.deleteDataRepositoryAssociation(ArgumentMatchers.any(DeleteDataRepositoryAssociationRequest.class)))
                .thenReturn(DeleteDataRepositoryAssociationResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 2)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, atLeastOnce()).deleteDataRepositoryAssociation(
                ArgumentMatchers.any(DeleteDataRepositoryAssociationRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
    }

    @Test
    public void handleRequest_ThrowsDRANotFound() {
        final DeleteHandler handler = new DeleteHandler();
        final String associationId = "dra-12345678";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .tags(new ArrayList<>())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(association)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(describeResponse) //First call to see that resource exists
                .thenThrow(DataRepositoryAssociationNotFoundException.builder().build()); //Second stabilize throws exception

        when(fsxClient.deleteDataRepositoryAssociation(ArgumentMatchers.any(DeleteDataRepositoryAssociationRequest.class)))
                .thenReturn(DeleteDataRepositoryAssociationResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, atLeastOnce()).deleteDataRepositoryAssociation(
                ArgumentMatchers.any(DeleteDataRepositoryAssociationRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DeleteHandler handler = new DeleteHandler();
        final String associationId = "dra-12345678";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .tags(new ArrayList<>())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(association)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(describeResponse) //First call to see that resource exists
                .thenReturn(describeResponse) // First call in stabilize
                .thenReturn(DescribeDataRepositoryAssociationsResponse.builder().build()); //Second stabilize that causes us to exit

        when(fsxClient.deleteDataRepositoryAssociation(ArgumentMatchers.any(DeleteDataRepositoryAssociationRequest.class)))
                .thenReturn(DeleteDataRepositoryAssociationResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, atLeastOnce()).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, atLeastOnce()).deleteDataRepositoryAssociation(
                ArgumentMatchers.any(DeleteDataRepositoryAssociationRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}

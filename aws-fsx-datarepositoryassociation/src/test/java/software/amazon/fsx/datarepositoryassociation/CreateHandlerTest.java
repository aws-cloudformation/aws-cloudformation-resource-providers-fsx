package software.amazon.fsx.datarepositoryassociation;

import java.time.Duration;
import java.util.ArrayList;

import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.CreateDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.CreateDataRepositoryAssociationResponse;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DataRepositoryLifecycle;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.InternalServerErrorException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<FSxClient> proxyClient;

    @Mock
    FSxClient fsxClient;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger,
                MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis(),
                FAST_DELAY_FACTORY);
        fsxClient = mock(FSxClient.class);
        proxyClient = mockProxy(proxy, fsxClient);
    }

    @AfterEach
    public void tear_down() {
        verify(fsxClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(fsxClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();
        final String associationId = "dra-12345678";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .tags(new ArrayList<>())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DataRepositoryAssociation availableAssociation = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse availableDescribeResponse =
                DescribeDataRepositoryAssociationsResponse.builder()
                        .associations(availableAssociation)
                        .build();

        final DataRepositoryAssociation creatingAssociation = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .lifecycle(DataRepositoryLifecycle.CREATING)
                .build();

        final DescribeDataRepositoryAssociationsResponse creatingDescribeResponse =
                DescribeDataRepositoryAssociationsResponse.builder()
                        .associations(creatingAssociation)
                        .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(creatingDescribeResponse) //Return DRA is in creating in stabilize
                .thenReturn(availableDescribeResponse); //Return DRA is in created in stabilize

        when(fsxClient.createDataRepositoryAssociation(ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class)))
                .thenReturn(CreateDataRepositoryAssociationResponse.builder()
                        .association(creatingAssociation)
                        .build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 3)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, atLeastOnce()).createDataRepositoryAssociation(
                ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleError() {
        final CreateHandler handler = new CreateHandler();
        final String associationId = "dra-12345678";

        final ResourceModel model = ResourceModel.builder()
                .associationId(associationId)
                .tags(new ArrayList<>())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(fsxClient.createDataRepositoryAssociation(ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class)))
                .thenThrow(InternalServerErrorException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, atLeastOnce()).createDataRepositoryAssociation(
                ArgumentMatchers.any(CreateDataRepositoryAssociationRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
    }
}

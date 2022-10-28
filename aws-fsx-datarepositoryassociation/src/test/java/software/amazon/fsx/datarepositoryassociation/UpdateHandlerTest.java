package software.amazon.fsx.datarepositoryassociation;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DataRepositoryLifecycle;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.EventType;
import software.amazon.awssdk.services.fsx.model.S3DataRepositoryConfiguration;
import software.amazon.awssdk.services.fsx.model.TagResourceRequest;
import software.amazon.awssdk.services.fsx.model.TagResourceResponse;
import software.amazon.awssdk.services.fsx.model.UntagResourceRequest;
import software.amazon.awssdk.services.fsx.model.UntagResourceResponse;
import software.amazon.awssdk.services.fsx.model.UpdateDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.UpdateDataRepositoryAssociationResponse;
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
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<FSxClient> proxyClient;

    @Mock
    FSxClient fsxClient;

    private boolean checkServiceName;
    private String associationId;
    private ResourceHandlerRequest<ResourceModel> request;
    private DescribeDataRepositoryAssociationsResponse availableDescribeResponsePreUpdate;
    private DescribeDataRepositoryAssociationsResponse updatingDescribeResponse;
    private DescribeDataRepositoryAssociationsResponse availableDescribeResponsePostUpdate;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger,
            MOCK_CREDENTIALS,
            () -> Duration.ofSeconds(600).toMillis(),
            FAST_DELAY_FACTORY);
        fsxClient = mock(FSxClient.class);
        proxyClient = mockProxy(proxy, fsxClient);
        associationId = "dra-12345678";
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
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .build();

        this.request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(prevModel)
                .desiredResourceState(newModel)
                .build();

        checkServiceName = false;

        assertThatThrownBy(() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnNotFoundException.class);
    }

    @Test
    public void handleRequest_NoPrev() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel newModel = ResourceModel.builder()
                .build();

        this.request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(newModel)
                .build();

        checkServiceName = false;

        assertThatThrownBy(() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnNotFoundException.class);
    }

    @Test
    public void handleRequest_DRADne() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .build();

        this.request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(prevModel)
                .desiredResourceState(newModel)
                .build();

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(DescribeDataRepositoryAssociationsResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).describeDataRepositoryAssociations(
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
    public void handleRequest_UpdateChunkSize() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .importedFileChunkSize(/*importedFileChunkSize*/ 2048)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.emptyList())
                .importedFileChunkSize(/*importedFileChunkSize*/ 4096)
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(updatingDescribeResponse)
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.updateDataRepositoryAssociation(ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class)))
                .thenReturn(UpdateDataRepositoryAssociationResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 5)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).updateDataRepositoryAssociation(
                ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class));

        commonAssertions(response);
    }

    @Test
    public void handleRequest_AutoImport() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.emptyList())
                .s3(S3.builder()
                        .autoImportPolicy(AutoImportPolicy.builder()
                                .events(Collections.singleton(EventType.NEW.name()))
                                .build())
                        .build())
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(updatingDescribeResponse)
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.updateDataRepositoryAssociation(ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class)))
                .thenReturn(UpdateDataRepositoryAssociationResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 5)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).updateDataRepositoryAssociation(
                ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class));

        commonAssertions(response);
    }

    @Test
    public void handleRequest_AutoExport() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.emptyList())
                .s3(S3.builder()
                        .autoExportPolicy(AutoExportPolicy.builder()
                                .events(Collections.singleton(EventType.NEW.name()))
                                .build())
                        .build())
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(updatingDescribeResponse)
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.updateDataRepositoryAssociation(ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class)))
                .thenReturn(UpdateDataRepositoryAssociationResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 5)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).updateDataRepositoryAssociation(
                ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class));

        commonAssertions(response);
    }

    @Test
    public void handleRequest_AddTags() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.singletonList(Tag.builder()
                        .key(/*key*/ "key")
                        .value(/*value*/ "odd eye")
                        .build()))
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Arrays.asList(Tag.builder()
                                .key(/*key*/ "key")
                                .value(/*value*/ "odd eye")
                                .build(),
                        Tag.builder()
                                .key(/*key*/ "key2")
                                .value(/*value*/ "sahara")
                                .build()))
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.tagResource(ArgumentMatchers.any(TagResourceRequest.class)))
                .thenReturn(TagResourceResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 3)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).tagResource(
                ArgumentMatchers.any(TagResourceRequest.class));

        commonAssertions(response);
    }

    @Test
    public void handleRequest_RemoveTags() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Arrays.asList(Tag.builder()
                                .key(/*key*/ "key")
                                .value(/*value*/ "odd eye")
                                .build(),
                        Tag.builder()
                                .key(/*key*/ "key2")
                                .value(/*value*/ "sahara")
                                .build()))
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.singletonList(Tag.builder()
                        .key(/*key*/ "key")
                        .value(/*value*/ "odd eye")
                        .build()))
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.untagResource(ArgumentMatchers.any(UntagResourceRequest.class)))
                .thenReturn(UntagResourceResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 3)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).untagResource(
                ArgumentMatchers.any(UntagResourceRequest.class));

        commonAssertions(response);
    }

    @Test
    public void handleRequest_UpdateAllNoOp() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.singletonList(Tag.builder()
                        .key(/*key*/ "key")
                        .value(/*value*/ "odd eye")
                        .build()))
                .importedFileChunkSize(/*importedFileChunkSize*/ 2048)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.singletonList(Tag.builder()
                        .key(/*key*/ "key2")
                        .value(/*value*/ "wind")
                        .build()))
                .importedFileChunkSize(/*importedFileChunkSize*/ 4096)
                .s3(S3.builder()
                        .autoImportPolicy(AutoImportPolicy.builder()
                                .events(Collections.singleton(EventType.NEW.name()))
                                .build())
                        .autoExportPolicy(AutoExportPolicy.builder()
                                .events(Collections.singleton(EventType.NEW.name()))
                                .build())
                        .build())
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.tagResource(ArgumentMatchers.any(TagResourceRequest.class)))
                .thenReturn(TagResourceResponse.builder().build());

        when(fsxClient.untagResource(ArgumentMatchers.any(UntagResourceRequest.class)))
                .thenReturn(UntagResourceResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 10)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).tagResource(
                ArgumentMatchers.any(TagResourceRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).untagResource(
                ArgumentMatchers.any(UntagResourceRequest.class));

        commonAssertions(response);
    }

    @Test
    public void handleRequest_UpdateAll() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.singletonList(Tag.builder()
                        .key(/*key*/ "key")
                        .value(/*value*/ "odd eye")
                        .build()))
                .importedFileChunkSize(/*importedFileChunkSize*/ 2048)
                .build();

        final ResourceModel newModel = ResourceModel.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .tags(Collections.singletonList(Tag.builder()
                        .key(/*key*/ "key2")
                        .value(/*value*/ "wind")
                        .build()))
                .importedFileChunkSize(/*importedFileChunkSize*/ 4096)
                .s3(S3.builder()
                        .autoImportPolicy(AutoImportPolicy.builder()
                                .events(Collections.singleton(EventType.NEW.name()))
                                .build())
                        .autoExportPolicy(AutoExportPolicy.builder()
                                .events(Collections.singleton(EventType.NEW.name()))
                                .build())
                        .build())
                .build();

        updateCommonVariables(prevModel, newModel);

        //Strict argument matching doesn't work because requestOverrideConfigs don't match.
        when(fsxClient.describeDataRepositoryAssociations(ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class)))
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(updatingDescribeResponse)
                .thenReturn(availableDescribeResponsePostUpdate)
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(updatingDescribeResponse)
                .thenReturn(availableDescribeResponsePostUpdate)
                .thenReturn(availableDescribeResponsePreUpdate)
                .thenReturn(updatingDescribeResponse)
                .thenReturn(availableDescribeResponsePostUpdate);

        when(fsxClient.updateDataRepositoryAssociation(ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class)))
                .thenReturn(UpdateDataRepositoryAssociationResponse.builder().build());

        when(fsxClient.tagResource(ArgumentMatchers.any(TagResourceRequest.class)))
                .thenReturn(TagResourceResponse.builder().build());

        when(fsxClient.untagResource(ArgumentMatchers.any(UntagResourceRequest.class)))
                .thenReturn(UntagResourceResponse.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 13)).describeDataRepositoryAssociations(
                ArgumentMatchers.any(DescribeDataRepositoryAssociationsRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 3)).updateDataRepositoryAssociation(
                ArgumentMatchers.any(UpdateDataRepositoryAssociationRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).tagResource(
                ArgumentMatchers.any(TagResourceRequest.class));

        verify(fsxClient, times(/*wantedNumberOfInvocations*/ 1)).untagResource(
                ArgumentMatchers.any(UntagResourceRequest.class));

        commonAssertions(response);
    }

    private void updateCommonVariables(final ResourceModel prevModel,
                                       final ResourceModel newModel) {
        this.request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(prevModel)
                .desiredResourceState(newModel)
                .build();

        final DataRepositoryAssociation availableAssociationPreUpdate = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .tags(Translator.translateTagsToSdk(prevModel.getTags()))
                .importedFileChunkSize(prevModel.getImportedFileChunkSize())
                .build();
        this.availableDescribeResponsePreUpdate = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(availableAssociationPreUpdate)
                .build();

        final DataRepositoryAssociation updatingAssociation = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .lifecycle(DataRepositoryLifecycle.UPDATING)
                .build();
        this.updatingDescribeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(updatingAssociation)
                .build();


        final DataRepositoryAssociation.Builder availableAssociationPostUpdateBuilder = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .resourceARN(associationId)
                .lifecycle(DataRepositoryLifecycle.MISCONFIGURED)
                .tags(Translator.translateTagsToSdk(newModel.getTags()))
                .importedFileChunkSize(newModel.getImportedFileChunkSize());

        final S3DataRepositoryConfiguration s3DataRepositoryConfiguration = Translator.convertS3ModelToSDK(newModel);
        if (s3DataRepositoryConfiguration != null) {
            availableAssociationPostUpdateBuilder.s3(s3DataRepositoryConfiguration);
        }
        this.availableDescribeResponsePostUpdate = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(availableAssociationPostUpdateBuilder.build())
                .build();
    }

    private void commonAssertions(final ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}

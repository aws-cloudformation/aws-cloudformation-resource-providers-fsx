package software.amazon.fsx.datarepositoryassociation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.fsx.model.CreateDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DataRepositoryLifecycle;
import software.amazon.awssdk.services.fsx.model.DeleteDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.EventType;
import software.amazon.awssdk.services.fsx.model.ResourceNotFoundException;
import software.amazon.awssdk.services.fsx.model.S3DataRepositoryConfiguration;
import software.amazon.awssdk.services.fsx.model.Tag;
import software.amazon.awssdk.services.fsx.model.TagResourceRequest;
import software.amazon.awssdk.services.fsx.model.UntagResourceRequest;
import software.amazon.awssdk.services.fsx.model.UpdateDataRepositoryAssociationRequest;
import software.amazon.fsx.common.handler.Tagging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class TranslatorTest {

    @Test
    public void testTranslateToCreateRequest_AllProperties() {
        final String clientToken = "client-token-1";

        final Tag tag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "scientist")
                .build();

        final Tagging.TagSet allTags = Tagging.TagSet.builder()
                .resourceTags(Collections.singleton(tag))
                .build();

        final S3 s3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        final ResourceModel model = ResourceModel.builder()
                .fileSystemId(/*fileSystemId*/ "fs-123456")
                .fileSystemPath(/*fileSystemPath*/ "/ns1/")
                .dataRepositoryPath(/*dataRepositoryPath*/ "s3://test")
                .batchImportMetaDataOnCreate(/*batchImportMetaDataOnCreate*/ true)
                .importedFileChunkSize(/*importedFileChunkSize*/ 2048)
                .s3(s3)
                .build();

        final CreateDataRepositoryAssociationRequest createRequest = Translator.translateToCreateRequest(model,
                allTags,
                clientToken);

        assertThat(createRequest.fileSystemId()).isEqualTo(model.getFileSystemId());
        assertThat(createRequest.fileSystemPath()).isEqualTo(model.getFileSystemPath());
        assertThat(createRequest.dataRepositoryPath()).isEqualTo(model.getDataRepositoryPath());
        assertThat(createRequest.batchImportMetaDataOnCreate()).isEqualTo(model.getBatchImportMetaDataOnCreate());
        assertThat(createRequest.importedFileChunkSize()).isEqualTo(model.getImportedFileChunkSize());
        assertThat(createRequest.s3().autoExportPolicy().events().stream()
                .map(Enum::name)
                .collect(Collectors.toSet()))
                .isEqualTo(model.getS3().getAutoExportPolicy().getEvents());
        assertThat(createRequest.s3().autoImportPolicy().events().stream()
                .map(Enum::name)
                .collect(Collectors.toSet()))
                .isEqualTo(model.getS3().getAutoImportPolicy().getEvents());
        assertThat(createRequest.clientRequestToken()).isEqualTo(clientToken);
        assertThat(createRequest.tags().size()).isEqualTo(1);
        assertThat(createRequest.tags().get(0)).isEqualTo(tag);
    }

    @Test
    public void testTranslateToCreateRequest_NullProperties() {
        final String clientToken = "client-token-1";

        final ResourceModel model = ResourceModel.builder()
                .fileSystemId(/*fileSystemId*/ "fs-123456")
                .fileSystemPath(/*fileSystemPath*/ "/ns1/")
                .dataRepositoryPath(/*dataRepositoryPath*/ "s3://test")
                .batchImportMetaDataOnCreate(/*batchImportMetaDataOnCreate*/ null)
                .importedFileChunkSize(/*importedFileChunkSize*/ null)
                .s3(/*s3*/ null)
                .build();

        final CreateDataRepositoryAssociationRequest createRequest = Translator.translateToCreateRequest(model,
                /*TagSet*/ null,
                clientToken);

        assertThat(createRequest.fileSystemId()).isEqualTo(model.getFileSystemId());
        assertThat(createRequest.fileSystemPath()).isEqualTo(model.getFileSystemPath());
        assertThat(createRequest.dataRepositoryPath()).isEqualTo(model.getDataRepositoryPath());
        assertThat(createRequest.batchImportMetaDataOnCreate()).isEqualTo(model.getBatchImportMetaDataOnCreate());
        assertThat(createRequest.importedFileChunkSize()).isEqualTo(model.getImportedFileChunkSize());
        assertThat(createRequest.s3()).isNull();
        assertThat(createRequest.clientRequestToken()).isEqualTo(clientToken);
        assertThat(createRequest.tags().isEmpty()).isTrue();
    }

    @Test
    public void testTranslateToCreateRequest_EmptyProperties() {
        final String clientToken = "client-token-1";

        final ResourceModel model = ResourceModel.builder()
                .fileSystemId(/*fileSystemId*/ "fs-123456")
                .fileSystemPath(/*fileSystemPath*/ "/ns1/")
                .dataRepositoryPath(/*dataRepositoryPath*/ "s3://test")
                .build();

        final CreateDataRepositoryAssociationRequest createRequest = Translator.translateToCreateRequest(model,
                Tagging.TagSet.builder().build(),
                clientToken);

        assertThat(createRequest.fileSystemId()).isEqualTo(model.getFileSystemId());
        assertThat(createRequest.fileSystemPath()).isEqualTo(model.getFileSystemPath());
        assertThat(createRequest.dataRepositoryPath()).isEqualTo(model.getDataRepositoryPath());
        assertThat(createRequest.batchImportMetaDataOnCreate()).isEqualTo(model.getBatchImportMetaDataOnCreate());
        assertThat(createRequest.importedFileChunkSize()).isEqualTo(model.getImportedFileChunkSize());
        assertThat(createRequest.s3()).isNull();
        assertThat(createRequest.clientRequestToken()).isEqualTo(clientToken);
        assertThat(createRequest.tags().isEmpty()).isTrue();
    }

    @Test
    public void testTranslateToReadRequest() {
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .build();

        final DescribeDataRepositoryAssociationsRequest describeRequest = Translator.translateToReadRequest(model);
        assertThat(describeRequest.associationIds().size()).isEqualTo(1);
        assertThat(describeRequest.associationIds().get(0)).isEqualTo(model.getAssociationId());
    }

    @Test
    public void testTranslateFromReadResponse_NullAssociation() {
        final String associationId = "dra-123456789";

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> Translator.translateFromReadResponse(describeResponse, associationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    public void testTranslateFromReadResponse_AllProperties() {
        final String associationId = "dra-123456789";

        final Tag tag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "scientist")
                .build();

        final S3DataRepositoryConfiguration s3DRAConfig = S3DataRepositoryConfiguration.builder()
                .autoExportPolicy(software.amazon.awssdk.services.fsx.model.AutoExportPolicy.builder()
                        .events(EventType.NEW,
                                EventType.DELETED,
                                EventType.CHANGED)
                        .build())
                .autoImportPolicy(software.amazon.awssdk.services.fsx.model.AutoImportPolicy.builder()
                        .events(EventType.NEW,
                                EventType.DELETED,
                                EventType.CHANGED)
                        .build())
                .build();

        final DataRepositoryAssociation expectedAssociation = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .resourceARN(/*resourceARN*/ "arn")
                .fileSystemId(/*fileSystemId*/ "fs-123456789")
                .fileSystemPath(/*fileSystemPath*/ "/ns1/")
                .dataRepositoryPath(/*dataRepositoryPath*/ "s3://test")
                .batchImportMetaDataOnCreate(/*batchImportMetaDataOnCreate*/ true)
                .importedFileChunkSize(/*importedFileChunkSize*/ 2048)
                .tags(/*tags*/ tag)
                .s3(s3DRAConfig)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(expectedAssociation)
                .build();
        final ResourceModel model = Translator.translateFromReadResponse(describeResponse, associationId);

        assertThat(expectedAssociation.associationId()).isEqualTo(model.getAssociationId());
        assertThat(expectedAssociation.resourceARN()).isEqualTo(model.getResourceARN());
        assertThat(expectedAssociation.fileSystemId()).isEqualTo(model.getFileSystemId());
        assertThat(expectedAssociation.fileSystemPath()).isEqualTo(model.getFileSystemPath());
        assertThat(expectedAssociation.dataRepositoryPath()).isEqualTo(model.getDataRepositoryPath());
        assertThat(expectedAssociation.batchImportMetaDataOnCreate()).isEqualTo(model.getBatchImportMetaDataOnCreate());
        assertThat(expectedAssociation.importedFileChunkSize()).isEqualTo(model.getImportedFileChunkSize());
        assertThat(expectedAssociation.s3().autoExportPolicy().events().stream()
                .map(Enum::name)
                .collect(Collectors.toSet()))
                .isEqualTo(model.getS3().getAutoExportPolicy().getEvents());
        assertThat(expectedAssociation.s3().autoImportPolicy().events().stream()
                .map(Enum::name)
                .collect(Collectors.toSet()))
                .isEqualTo(model.getS3().getAutoImportPolicy().getEvents());
        assertThat(expectedAssociation.tags().size()).isEqualTo(1);
        assertThat(expectedAssociation.tags().get(0)).isEqualTo(tag);
    }

    @Test
    public void testTranslateFromReadResponse_NullProperties() {
        final String associationId = "dra-123456789";

        final Tag tag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "scientist")
                .build();

        final DataRepositoryAssociation expectedAssociation = DataRepositoryAssociation.builder()
                .associationId(associationId)
                .resourceARN(/*resourceARN*/ "arn")
                .fileSystemId(/*fileSystemId*/ "fs-123456789")
                .fileSystemPath(/*fileSystemPath*/ "/ns1/")
                .dataRepositoryPath(/*dataRepositoryPath*/ "s3://test")
                .batchImportMetaDataOnCreate(/*batchImportMetaDataOnCreate*/ true)
                .importedFileChunkSize(/*importedFileChunkSize*/ 2048)
                .tags(/*tags*/ tag)
                .lifecycle(DataRepositoryLifecycle.AVAILABLE)
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(expectedAssociation)
                .build();
        final ResourceModel model = Translator.translateFromReadResponse(describeResponse, associationId);

        assertThat(expectedAssociation.associationId()).isEqualTo(model.getAssociationId());
        assertThat(expectedAssociation.resourceARN()).isEqualTo(model.getResourceARN());
        assertThat(expectedAssociation.fileSystemId()).isEqualTo(model.getFileSystemId());
        assertThat(expectedAssociation.fileSystemPath()).isEqualTo(model.getFileSystemPath());
        assertThat(expectedAssociation.dataRepositoryPath()).isEqualTo(model.getDataRepositoryPath());
        assertThat(expectedAssociation.batchImportMetaDataOnCreate()).isEqualTo(model.getBatchImportMetaDataOnCreate());
        assertThat(expectedAssociation.importedFileChunkSize()).isEqualTo(model.getImportedFileChunkSize());
        assertThat(model.getS3()).isNull();
        assertThat(expectedAssociation.tags().size()).isEqualTo(1);
        assertThat(expectedAssociation.tags().get(0)).isEqualTo(tag);
    }

    @Test
    public void testTranslateToDeleteRequest() {
        final String clientToken = "client-token-1";

        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .build();

        final DeleteDataRepositoryAssociationRequest deleteRequest = Translator.translateToDeleteRequest(model, clientToken);
        assertThat(deleteRequest.associationId()).isEqualTo(model.getAssociationId());
        assertThat(deleteRequest.clientRequestToken()).isEqualTo(clientToken);
        assertThat(deleteRequest.deleteDataInFileSystem()).isFalse();
    }

    @Test
    public void testShouldUpdateImportedFileChunkSize_OneNull() {
        assertThat(Translator.shouldUpdateImportedFileChunkSize(/*newImportedFileChunkSize*/ null,
                /*oldImportedFileChunkSize*/ 1)).isTrue();
    }

    @Test
    public void testShouldUpdateImportedFileChunkSize_BothNull() {
        assertThat(Translator.shouldUpdateImportedFileChunkSize(/*newImportedFileChunkSize*/ null,
                /*oldImportedFileChunkSize*/ null)).isFalse();
    }

    @Test
    public void testShouldUpdateImportedFileChunkSize_Same() {
        assertThat(Translator.shouldUpdateImportedFileChunkSize(/*newImportedFileChunkSize*/ 1,
                /*oldImportedFileChunkSize*/ 1)).isFalse();
    }

    @Test
    public void testShouldUpdateImportedFileChunkSize_Different() {
        assertThat(Translator.shouldUpdateImportedFileChunkSize(/*newImportedFileChunkSize*/ 1,
                /*oldImportedFileChunkSize*/ 2)).isTrue();
    }

    @Test
    public void testTranslateToUpdateImportedFileChunkSize() {
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .importedFileChunkSize(1)
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest =
                Translator.translateToUpdateImportedFileChunkSize(model);

        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());
        assertThat(updateRequest.importedFileChunkSize()).isEqualTo(model.getImportedFileChunkSize());
    }

    @Test
    public void testShouldUpdateS3ImportPolicy_OneNullS3() {
        final S3 oldS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ImportPolicy(oldS3, /*newS3*/ null)).isTrue();
    }

    @Test
    public void testShouldUpdateS3ImportPolicy_TwoNullS3() {
        assertThat(Translator.shouldUpdateS3ImportPolicy(/*oldS3*/ null, /*newS3*/ null)).isFalse();
    }

    @Test
    public void testShouldUpdateS3ImportPolicy_OneNullAutoImportPolicy() {
        final S3 oldS3 = S3.builder()
                .build();

        final S3 newS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ImportPolicy(oldS3, newS3)).isTrue();
    }

    @Test
    public void testShouldUpdateS3ImportPolicy_TwoNullAutoImportPolicy() {
        final S3 oldS3 = S3.builder()
                .build();

        final S3 newS3 = S3.builder()
                .build();

        assertThat(Translator.shouldUpdateS3ImportPolicy(oldS3, newS3)).isFalse();
    }

    @Test
    public void testShouldUpdateS3ImportPolicy_SameAutoImportPolicy() {
        final S3 oldS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        final S3 newS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ImportPolicy(oldS3, newS3)).isFalse();
    }

    @Test
    public void testShouldUpdateS3ImportPolicy_DifferentAutoImportPolicy() {
        final S3 oldS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(Collections.singleton(EventType.NEW.name()))
                        .build())
                .build();

        final S3 newS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ImportPolicy(oldS3, newS3)).isTrue();
    }

    @Test
    public void testUpdateS3ImportPolicy_NullConfig() {
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest = Translator.updateS3ImportPolicy(model);
        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());
        assertThat(updateRequest.s3()).isNull();
    }

    @Test
    public void testUpdateS3ImportPolicy_NullImportPolicy() {
        final S3 s3 = S3.builder()
                .build();
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .s3(s3)
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest = Translator.updateS3ImportPolicy(model);
        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());
        assertThat(updateRequest.s3().autoImportPolicy().events().isEmpty()).isTrue();
    }

    @Test
    public void testUpdateS3ImportPolicy_ValidImportPolicy() {
        final S3 s3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .s3(s3)
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest = Translator.updateS3ImportPolicy(model);
        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());

        assertThat(updateRequest.s3().autoImportPolicy().events().size())
                .isEqualTo(s3.getAutoImportPolicy().getEvents().size());
        updateRequest.s3().autoImportPolicy().events()
                        .forEach(event -> assertThat(s3.getAutoImportPolicy().getEvents().contains(event.name())));
    }

    @Test
    public void testShouldUpdateS3ExportPolicy_OneNullS3() {
        final S3 oldS3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ExportPolicy(oldS3, /*newS3*/ null)).isTrue();
    }

    @Test
    public void testShouldUpdateS3ExportPolicy_TwoNullS3() {
        assertThat(Translator.shouldUpdateS3ExportPolicy(/*oldS3*/ null, /*newS3*/ null)).isFalse();
    }

    @Test
    public void testShouldUpdateS3ExportPolicy_OneNullAutoExportPolicy() {
        final S3 oldS3 = S3.builder()
                .build();

        final S3 newS3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ExportPolicy(oldS3, newS3)).isTrue();
    }

    @Test
    public void testShouldUpdateS3ExportPolicy_TwoNullAutoExportPolicy() {
        final S3 oldS3 = S3.builder()
                .build();

        final S3 newS3 = S3.builder()
                .build();

        assertThat(Translator.shouldUpdateS3ExportPolicy(oldS3, newS3)).isFalse();
    }

    @Test
    public void testShouldUpdateS3ExportPolicy_SameAutoExportPolicy() {
        final S3 oldS3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        final S3 newS3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ExportPolicy(oldS3, newS3)).isFalse();
    }

    @Test
    public void testShouldUpdateS3ExportPolicy_DifferentAutoExportPolicy() {
        final S3 oldS3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(Collections.singleton(EventType.NEW.name()))
                        .build())
                .build();

        final S3 newS3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        assertThat(Translator.shouldUpdateS3ExportPolicy(oldS3, newS3)).isTrue();
    }

    @Test
    public void testUpdateS3ExportPolicy_NullConfig() {
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest = Translator.updateS3ExportPolicy(model);
        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());
        assertThat(updateRequest.s3()).isNull();
    }

    @Test
    public void testUpdateS3ExportPolicy_NullExportPolicy() {
        final S3 s3 = S3.builder()
                .build();
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .s3(s3)
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest = Translator.updateS3ExportPolicy(model);
        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());
        assertThat(updateRequest.s3().autoExportPolicy().events().isEmpty()).isTrue();
    }

    @Test
    public void testUpdateS3ExportPolicy_ValidExportPolicy() {
        final S3 s3 = S3.builder()
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();
        final ResourceModel model = ResourceModel.builder()
                .associationId(/*associationId*/ "dra-123456")
                .s3(s3)
                .build();

        final UpdateDataRepositoryAssociationRequest updateRequest = Translator.updateS3ExportPolicy(model);
        assertThat(updateRequest.associationId()).isEqualTo(model.getAssociationId());

        assertThat(updateRequest.s3().autoExportPolicy().events().size())
                .isEqualTo(s3.getAutoExportPolicy().getEvents().size());
        updateRequest.s3().autoExportPolicy().events()
                .forEach(event -> assertThat(s3.getAutoExportPolicy().getEvents().contains(event.name())));
    }

    @Test
    public void testTranslateToListRequest() {
        final String token = "yoy";
        final DescribeDataRepositoryAssociationsRequest describeRequest = Translator.translateToListRequest(token);
        assertThat(describeRequest.nextToken()).isEqualTo(token);
    }

    @Test
    public void testTranslateFromListRequest_NoAssociations() {
        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .build();
        final List<ResourceModel> models = Translator.translateFromListRequest(describeResponse);

        assertThat(models.isEmpty()).isTrue();
    }

    @Test
    public void testTranslateFromListRequest() {
        final DataRepositoryAssociation dra1 = DataRepositoryAssociation.builder()
                .associationId("dra-012345678")
                .build();
        final DataRepositoryAssociation dra2 = DataRepositoryAssociation.builder()
                .associationId("dra-0123456789")
                .build();

        final DescribeDataRepositoryAssociationsResponse describeResponse = DescribeDataRepositoryAssociationsResponse.builder()
                .associations(dra1, dra2)
                .build();
        final List<ResourceModel> models = Translator.translateFromListRequest(describeResponse);
        final List<String> associationIdsFromModel = models.stream()
                .map(ResourceModel::getAssociationId)
                .collect(Collectors.toList());

        assertThat(models.size()).isEqualTo(2);
        assertThat(associationIdsFromModel.contains(dra1.associationId())).isTrue();
        assertThat(associationIdsFromModel.contains(dra2.associationId())).isTrue();
    }

    @Test
    public void testTranslateToTagResourceRequest() {
        final String arn = "dra-123456789";

        final Tag tag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "m&m")
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .resourceARN(arn)
                .build();

        final TagResourceRequest tagResourceRequest = Translator.translateToTagResourceRequest(association,
                Collections.singletonMap(tag.key(), tag.value()));

        assertThat(tagResourceRequest.resourceARN()).isEqualTo(arn);
        assertThat(tagResourceRequest.tags().size()).isEqualTo(1);
        assertThat(tagResourceRequest.tags().get(0)).isEqualTo(tag);
    }

    @Test
    public void testTranslateToUntagResourceRequest() {
        final String arn = "dra-123456789";

        final Tag tag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "m&m")
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .resourceARN(arn)
                .build();

        final UntagResourceRequest untagResourceRequest = Translator.translateToUntagResourceRequest(association,
                Collections.singleton(tag.key()));

        assertThat(untagResourceRequest.resourceARN()).isEqualTo(arn);
        assertThat(untagResourceRequest.tagKeys().size()).isEqualTo(1);
        assertThat(untagResourceRequest.tagKeys().get(0)).isEqualTo(tag.key());
    }

    @Test
    public void testTranslateTagsToSdk() {
        final software.amazon.fsx.datarepositoryassociation.Tag modelTag =
                software.amazon.fsx.datarepositoryassociation.Tag.builder()
                        .key(/*key*/ "key1")
                        .value(/*value*/ "m&m")
                        .build();
        final Tag fsxTag = Tag.builder()
                .key(modelTag.getKey())
                .value(modelTag.getValue())
                .build();

        final Set<Tag> tags = Translator.translateTagsToSdk(Collections.singletonList(modelTag));

        assertThat(tags.size()).isEqualTo(1);
        assertThat(tags.contains(fsxTag)).isTrue();
    }

    @Test
    public void testTranslateTagsToSdk_NullTags() {
        final Set<Tag> tags = Translator.translateTagsToSdk(/*tags*/ null);
        assertThat(tags.isEmpty()).isTrue();
    }

    @Test
    public void testTranslateTagsToSdkFromMap() {
        final Tag fsxTag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "m&m")
                .build();

        final Set<Tag> tags = Translator.translateTagsToSdkFromMap(
                Collections.singletonMap(fsxTag.key(), fsxTag.value()));

        assertThat(tags.size()).isEqualTo(1);
        assertThat(tags.contains(fsxTag)).isTrue();
    }

    @Test
    public void testTranslateTagsToSdkFromMap_NullTags() {
        final Set<Tag> tags = Translator.translateTagsToSdkFromMap(/*tags*/ null);
        assertThat(tags.isEmpty()).isTrue();
    }

    @Test
    public void testTranslateTagsToModel() {
        final Tag fsxTag = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "m&m")
                .build();
        final software.amazon.fsx.datarepositoryassociation.Tag modelTag =
                software.amazon.fsx.datarepositoryassociation.Tag.builder()
                        .key(fsxTag.key())
                        .value(fsxTag.value())
                        .build();

        final List<software.amazon.fsx.datarepositoryassociation.Tag> tags = Translator.translateTagsToModel(
                Collections.singleton(fsxTag));

        assertThat(tags.size()).isEqualTo(1);
        assertThat(tags.contains(modelTag)).isTrue();
    }

    @Test
    public void testTranslateTagsToModel_NullTags() {
        final List<software.amazon.fsx.datarepositoryassociation.Tag> tags = Translator.translateTagsToModel(/*tags*/ null);
        assertThat(tags.isEmpty()).isTrue();
    }

    @Test
    public void testConvertS3ModelToSDK_NullS3() {
        final ResourceModel model = ResourceModel.builder()
                .build();
        final S3DataRepositoryConfiguration actualS3SDK = Translator.convertS3ModelToSDK(model);

        assertThat(actualS3SDK).isNull();
    }

    @Test
    public void testConvertS3ModelToSDK_NullPolicies() {
        final S3 modelS3 = S3.builder()
                .build();

        final S3DataRepositoryConfiguration expectedSDKS3 = S3DataRepositoryConfiguration.builder()
                .build();

        final ResourceModel model = ResourceModel.builder()
                .s3(modelS3)
                .build();

        final S3DataRepositoryConfiguration actualS3SDK = Translator.convertS3ModelToSDK(model);

        assertThat(actualS3SDK).isEqualTo(expectedSDKS3);
    }

    @Test
    public void testConvertS3ModelToSDK() {
        final S3 modelS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW.name(),
                                EventType.CHANGED.name(),
                                EventType.DELETED.name())))
                        .build())
                .build();

        final S3DataRepositoryConfiguration expectedSDKS3 = S3DataRepositoryConfiguration.builder()
                .autoImportPolicy(software.amazon.awssdk.services.fsx.model.AutoImportPolicy.builder()
                        .eventsWithStrings(modelS3.getAutoImportPolicy().getEvents())
                        .build())
                .autoExportPolicy(software.amazon.awssdk.services.fsx.model.AutoExportPolicy.builder()
                        .eventsWithStrings(modelS3.getAutoExportPolicy().getEvents())
                        .build())
                .build();

        final ResourceModel model = ResourceModel.builder()
                .s3(modelS3)
                .build();

        final S3DataRepositoryConfiguration actualS3SDK = Translator.convertS3ModelToSDK(model);

        assertThat(actualS3SDK).isEqualTo(expectedSDKS3);
    }

    @Test
    public void testConvertS3SDKToModel_NullS3() {
        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .build();
        final S3 actualS3Model = Translator.convertS3SDKToModel(association);

        assertThat(actualS3Model).isNull();
    }

    @Test
    public void testConvertS3SDKToModel_NullPolicies() {
        final S3DataRepositoryConfiguration sdkS3 = S3DataRepositoryConfiguration.builder()
                .build();

        final S3 expectedModelS3 = S3.builder()
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .s3(sdkS3)
                .build();
        final S3 actualS3Model = Translator.convertS3SDKToModel(association);

        assertThat(actualS3Model).isEqualTo(expectedModelS3);
    }

    @Test
    public void testConvertS3SDKToModel() {
        final S3DataRepositoryConfiguration sdkS3 = S3DataRepositoryConfiguration.builder()
                .autoImportPolicy(software.amazon.awssdk.services.fsx.model.AutoImportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW,
                                EventType.CHANGED,
                                EventType.DELETED)))
                        .build())
                .autoExportPolicy(software.amazon.awssdk.services.fsx.model.AutoExportPolicy.builder()
                        .events(new HashSet<>(Arrays.asList(EventType.NEW,
                                EventType.CHANGED,
                                EventType.DELETED)))
                        .build())
                .build();

        final S3 expectedModelS3 = S3.builder()
                .autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(sdkS3.autoImportPolicy().eventsAsStrings()))
                        .build())
                .autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(sdkS3.autoExportPolicy().eventsAsStrings()))
                        .build())
                .build();

        final DataRepositoryAssociation association = DataRepositoryAssociation.builder()
                .s3(sdkS3)
                .build();
        final S3 actualS3Model = Translator.convertS3SDKToModel(association);

        assertThat(actualS3Model).isEqualTo(expectedModelS3);
    }
}

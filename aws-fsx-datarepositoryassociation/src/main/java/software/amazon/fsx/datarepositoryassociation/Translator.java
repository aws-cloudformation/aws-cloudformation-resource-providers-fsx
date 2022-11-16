package software.amazon.fsx.datarepositoryassociation;

import software.amazon.awssdk.services.fsx.model.CreateDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.DataRepositoryAssociation;
import software.amazon.awssdk.services.fsx.model.DeleteDataRepositoryAssociationRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeDataRepositoryAssociationsResponse;
import software.amazon.awssdk.services.fsx.model.ResourceNotFoundException;
import software.amazon.awssdk.services.fsx.model.S3DataRepositoryConfiguration;
import software.amazon.awssdk.services.fsx.model.TagResourceRequest;
import software.amazon.awssdk.services.fsx.model.UntagResourceRequest;
import software.amazon.awssdk.services.fsx.model.UpdateDataRepositoryAssociationRequest;
import software.amazon.fsx.common.handler.Tagging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */

public final class Translator {
    private Translator() {
    }

    /**
     * Request to create a resource
     *
     * @param model resource model
     * @return createRequest the fsx service request to create a resource
     */
    static CreateDataRepositoryAssociationRequest translateToCreateRequest(final ResourceModel model,
                                                                           final Tagging.TagSet tagSet,
                                                                           final String clientToken) {
        final CreateDataRepositoryAssociationRequest.Builder createRequestBuilder = CreateDataRepositoryAssociationRequest.builder()
                .fileSystemId(model.getFileSystemId())
                .fileSystemPath(model.getFileSystemPath())
                .dataRepositoryPath(model.getDataRepositoryPath())
                .clientRequestToken(clientToken);

        if (model.getBatchImportMetaDataOnCreate() != null) {
            createRequestBuilder.batchImportMetaDataOnCreate(model.getBatchImportMetaDataOnCreate());
        }

        if (model.getImportedFileChunkSize() != null) {
            createRequestBuilder.importedFileChunkSize(model.getImportedFileChunkSize());
        }

        if (tagSet != null && !tagSet.isEmpty()) {
            createRequestBuilder.tags(Tagging.translateTagSetToSdk(tagSet));
        }

        final S3DataRepositoryConfiguration s3DRAConfig = convertS3ModelToSDK(model);
        if (s3DRAConfig != null) {
            createRequestBuilder.s3(s3DRAConfig);
        }


        return createRequestBuilder.build();
    }

    /**
     * Request to read a resource
     *
     * @param model resource model
     * @return describeRequest the fsx service request to describe a resource
     */
    static DescribeDataRepositoryAssociationsRequest translateToReadRequest(final ResourceModel model) {
        return DescribeDataRepositoryAssociationsRequest.builder()
                .associationIds(model.getAssociationId())
                .build();
    }

    /**
     * Translates resource object from sdk into a resource model
     *
     * @param describeResponse the fsx service describe resource response
     * @return model resource model
     */
    static ResourceModel translateFromReadResponse(final DescribeDataRepositoryAssociationsResponse describeResponse,
                                                   final String associationId) {
        final DataRepositoryAssociation association = DataRepositoryAssociationUtils.getDRAFromDescribeResponse(describeResponse);
        if (association != null) {
            final ResourceModel.ResourceModelBuilder builder = ResourceModel.builder()
                    .associationId(association.associationId())
                    .resourceARN(association.resourceARN())
                    .fileSystemId(association.fileSystemId())
                    .fileSystemPath(association.fileSystemPath())
                    .dataRepositoryPath(association.dataRepositoryPath())
                    .batchImportMetaDataOnCreate(association.batchImportMetaDataOnCreate())
                    .importedFileChunkSize(association.importedFileChunkSize())
                    .tags(translateTagsToModel(association.tags()));


            final S3 sdkS3 = convertS3SDKToModel(association);
            if (sdkS3 != null) {
                builder.s3(sdkS3);
            }

            return builder.build();
        } else {
            throw ResourceNotFoundException.builder()
                    .message(String.format("Data repository association does not exist for: %s.",
                            associationId))
                    .build();
        }
    }

    /**
     * Request to delete a resource
     *
     * @param model resource model
     * @return deleteRequest the fsx service request to delete a resource
     */
    static DeleteDataRepositoryAssociationRequest translateToDeleteRequest(final ResourceModel model,
                                                                           final String clientToken) {
        return DeleteDataRepositoryAssociationRequest.builder()
                .associationId(model.getAssociationId())
                .clientRequestToken(clientToken)
                .deleteDataInFileSystem(/*deleteDataInFileSystem*/ false)
                .build();
    }

    /**
     * Determines if we should update ImportedFileChunkSize.
     * @param newImportedFileChunkSize ImportedFileChunkSize from the new model.
     * @param oldImportedFileChunkSize ImportedFileChunkSize from the old model.
     * @return If we should update ImportedFileChunkSize.
     */
    static boolean shouldUpdateImportedFileChunkSize(final Integer newImportedFileChunkSize,
                                                     final Integer oldImportedFileChunkSize) {
        return !Objects.equals(newImportedFileChunkSize, oldImportedFileChunkSize);
    }

    /**
     * Request to update ImportedFileChunkSize.
     *
     * @param newModel The resource model.
     * @return The fsx service request to modify ImportedFileChunkSize.
     */
    static UpdateDataRepositoryAssociationRequest translateToUpdateImportedFileChunkSize(final ResourceModel newModel) {
        return UpdateDataRepositoryAssociationRequest.builder()
                .associationId(newModel.getAssociationId())
                .importedFileChunkSize(newModel.getImportedFileChunkSize())
                .build();
    }

    /**
     * Determines if we should update S3ImportPolicy.
     * @param newS3 S3 from the new model.
     * @param oldS3 S3 from the old model.
     * @return If we should update S3ImportPolicy.
     */
    static boolean shouldUpdateS3ImportPolicy(final S3 newS3,
                                              final S3 oldS3) {
        //Make the auto import policy empty if anything is null to make sure that null auto import policies/s3 structures
        //  equal empty auto import policies
        final AutoImportPolicy emptyAutoImport = new AutoImportPolicy();
        final AutoImportPolicy newAutoImport = newS3 == null ? emptyAutoImport :
                newS3.getAutoImportPolicy() == null ? emptyAutoImport : newS3.getAutoImportPolicy();
        final AutoImportPolicy oldAutoImport = oldS3 == null ? emptyAutoImport :
                oldS3.getAutoImportPolicy() == null ? emptyAutoImport : oldS3.getAutoImportPolicy();
        return !Objects.equals(newAutoImport, oldAutoImport);
    }


    /**
     * Request to update S3ImportPolicy.
     *
     * @param newModel The resource model.
     * @return The fsx service request to modify S3ImportPolicy.
     */
    static UpdateDataRepositoryAssociationRequest updateS3ImportPolicy(final ResourceModel newModel) {
        final UpdateDataRepositoryAssociationRequest.Builder updateRequestBuilder =
                UpdateDataRepositoryAssociationRequest.builder();
        updateRequestBuilder.associationId(newModel.getAssociationId());

        S3DataRepositoryConfiguration s3DRAConfig = convertS3ModelToSDK(newModel);
        if (s3DRAConfig != null) {
            final software.amazon.awssdk.services.fsx.model.AutoImportPolicy importPolicy = s3DRAConfig.autoImportPolicy();
            if (importPolicy != null) {
                s3DRAConfig = S3DataRepositoryConfiguration.builder()
                        .autoImportPolicy(s3DRAConfig.autoImportPolicy())
                        .build();
           // If the import config is null, and we got here, it means we need to delete the import policy.
            } else {
                s3DRAConfig = S3DataRepositoryConfiguration.builder()
                        .autoImportPolicy(software.amazon.awssdk.services.fsx.model.AutoImportPolicy.builder()
                                .events(new ArrayList<>())
                                .build())
                        .build();
            }
            updateRequestBuilder.s3(s3DRAConfig);
        }

        return updateRequestBuilder.build();
    }

    /**
     * Determines if we should update S3ExportPolicy.
     * @param newS3 S3 from the new model.
     * @param oldS3 S3 from the old model.
     * @return If we should update S3ExportPolicy.
     */
    static boolean shouldUpdateS3ExportPolicy(final S3 newS3,
                                              final S3 oldS3) {
        //Make the auto export policy empty if anything is null to make sure that null auto export policies/s3 structures
        //  equal empty auto export policies
        final AutoExportPolicy emptyAutoExport = new AutoExportPolicy();
        final AutoExportPolicy newAutoExport = newS3 == null ? emptyAutoExport :
                newS3.getAutoExportPolicy() == null ? emptyAutoExport : newS3.getAutoExportPolicy();
        final AutoExportPolicy oldAutoExport = oldS3 == null ? emptyAutoExport :
                oldS3.getAutoExportPolicy() == null ? emptyAutoExport : oldS3.getAutoExportPolicy();
        return !Objects.equals(newAutoExport, oldAutoExport);
    }


    /**
     * Request to update S3ExportPolicy.
     *
     * @param newModel The resource model.
     * @return The fsx service request to modify S3ExportPolicy.
     */
    static UpdateDataRepositoryAssociationRequest updateS3ExportPolicy(final ResourceModel newModel) {
        final UpdateDataRepositoryAssociationRequest.Builder updateRequestBuilder =
                UpdateDataRepositoryAssociationRequest.builder();
        updateRequestBuilder.associationId(newModel.getAssociationId());

        S3DataRepositoryConfiguration s3DRAConfig = convertS3ModelToSDK(newModel);
        if (s3DRAConfig != null) {
            final software.amazon.awssdk.services.fsx.model.AutoExportPolicy exportPolicy = s3DRAConfig.autoExportPolicy();
            if (exportPolicy != null) {
                s3DRAConfig = S3DataRepositoryConfiguration.builder()
                        .autoExportPolicy(s3DRAConfig.autoExportPolicy())
                        .build();
            // If the export config is null, and we got here, it means we need to delete the export policy.
            } else {
                s3DRAConfig = S3DataRepositoryConfiguration.builder()
                        .autoExportPolicy(software.amazon.awssdk.services.fsx.model.AutoExportPolicy.builder()
                                .events(new ArrayList<>())
                                .build())
                        .build();
            }
            System.out.println("Export config: " + s3DRAConfig);
            updateRequestBuilder.s3(s3DRAConfig);
        }

        return updateRequestBuilder.build();
    }

    /**
     * Request to list resources
     *
     * @param nextToken token passed to the fsx service list resources request
     * @return describeRequest the fsx service request to list resources within the aws account
     */
    static DescribeDataRepositoryAssociationsRequest translateToListRequest(final String nextToken) {
        return DescribeDataRepositoryAssociationsRequest.builder()
                .nextToken(nextToken)
                .build();
    }

    /**
     * Translates resource objects from sdk into a resource model (primary identifier only)
     *
     * @param describeResponse the fsx service describe resource response
     * @return list of resource models
     */
    static List<ResourceModel> translateFromListRequest(final DescribeDataRepositoryAssociationsResponse describeResponse) {
        return streamOfOrEmpty(describeResponse.associations())
                .map(resource -> ResourceModel.builder()
                        .associationId(resource.associationId())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Returns a stream or an empty one for a collection.
     * @param collection The collection to convert.
     * @param <T> The type of the collection.
     * @return The converted collection.
     */
    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
                .map(Collection::stream)
                .orElseGet(Stream::empty);
    }

    /**
     * Request to add tags to a resource
     *
     * @param model resource model
     * @return tagRequest the fsx service request to tag a resource
     */
    static TagResourceRequest translateToTagResourceRequest(final DataRepositoryAssociation association,
                                                            final Map<String, String> addedTags) {
        final TagResourceRequest.Builder tagRequest = TagResourceRequest.builder();
        tagRequest.resourceARN(association.resourceARN());
        tagRequest.tags(translateTagsToSdkFromMap(addedTags));
        return tagRequest.build();
    }

    /**
     * Request to add tags to a resource
     *
     * @param association The DRA
     * @param removedTags The tags to remove
     * @return untagRequest the fsx service request to untag a resource
     */
    static UntagResourceRequest translateToUntagResourceRequest(final DataRepositoryAssociation association,
                                                                final Set<String> removedTags) {
        final UntagResourceRequest.Builder untagRequest = UntagResourceRequest.builder();
        untagRequest.resourceARN(association.resourceARN());
        untagRequest.tagKeys(removedTags);
        return untagRequest.build();
    }

    /**
     * Converts model tags to FSx SDK tags.
     * @param tags The model tags.
     * @return The FSx SDK tags.
     */
    static Set<software.amazon.awssdk.services.fsx.model.Tag> translateTagsToSdk(final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .map(tag -> software.amazon.awssdk.services.fsx.model.Tag.builder()
                        .key(tag.getKey())
                        .value(tag.getValue())
                        .build())
                .collect(Collectors.toSet());
    }

    /**
     * Converts key/value tags to FSx SDK tags.
     * @param tags The key/value tags.
     * @return The FSx SDK tags.
     */
    static Set<software.amazon.awssdk.services.fsx.model.Tag> translateTagsToSdkFromMap(final Map<String, String> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptyMap())
                .entrySet()
                .stream()
                .map(tag -> software.amazon.awssdk.services.fsx.model.Tag.builder()
                        .key(tag.getKey())
                        .value(tag.getValue())
                        .build())
                .collect(Collectors.toSet());
    }

    /**
     * Converts FSx SDK tags to model tags.
     * @param tags The FSx SDK tags.
     * @return The model tags.
     */
    static List<software.amazon.fsx.datarepositoryassociation.Tag> translateTagsToModel(
            final Collection<software.amazon.awssdk.services.fsx.model.Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .map(tag -> software.amazon.fsx.datarepositoryassociation.Tag.builder()
                        .key(tag.key())
                        .value(tag.value())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Convert the model's S3 object to FSx S3 SDK object.
     * @param model The model object.
     * @return The FSx S3 SDK object.
     */
    static S3DataRepositoryConfiguration convertS3ModelToSDK(final ResourceModel model) {
        final S3 s3 = model.getS3();
        S3DataRepositoryConfiguration.Builder s3DRAConfig = null;
        if (s3 != null) {
            s3DRAConfig = S3DataRepositoryConfiguration.builder();

            if (s3.getAutoImportPolicy() != null) {
                s3DRAConfig.autoImportPolicy(software.amazon.awssdk.services.fsx.model.AutoImportPolicy.builder()
                        .eventsWithStrings(s3.getAutoImportPolicy().getEvents())
                        .build());
            }

            if (s3.getAutoExportPolicy() != null) {
                s3DRAConfig.autoExportPolicy(software.amazon.awssdk.services.fsx.model.AutoExportPolicy.builder()
                        .eventsWithStrings(s3.getAutoExportPolicy().getEvents())
                        .build());
            }
        }
        return s3DRAConfig == null ? null : s3DRAConfig.build();
    }

    /**
     * Convert the FSx S3 SDK association to the model's S3 object.
     * @param association The FSx S3 SDK association.
     * @return The model's S3 object.
     */
    static S3 convertS3SDKToModel(final DataRepositoryAssociation association) {
        final S3DataRepositoryConfiguration sdkS3 = association.s3();
        S3.S3Builder s3ModelBuilder = null;
        if (sdkS3 != null) {
            s3ModelBuilder = S3.builder();

            if (sdkS3.autoImportPolicy() != null) {
                s3ModelBuilder.autoImportPolicy(AutoImportPolicy.builder()
                        .events(new HashSet<>(sdkS3.autoImportPolicy().eventsAsStrings()))
                        .build());
            }

            if (sdkS3.autoExportPolicy() != null) {
                s3ModelBuilder.autoExportPolicy(AutoExportPolicy.builder()
                        .events(new HashSet<>(sdkS3.autoExportPolicy().eventsAsStrings()))
                        .build());
            }
        }
        return s3ModelBuilder == null ? null : s3ModelBuilder.build();
    }
}

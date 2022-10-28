package software.amazon.fsx.datarepositoryassociation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public final class TagHelper {
    private TagHelper() {
    }

    /**
     * convertToMap
     * <p>
     * Converts a collection of Tag objects to a tag-name to tag-value map.
     * <p>
     * Note: Tag objects with null tag values will not be included in the output
     * map.
     *
     * @param tags Collection of tags to convert
     * @return Converted Map of tags
     */
    public static Map<String, String> convertToMap(final Collection<Tag> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return Collections.emptyMap();
        }
        return tags.stream()
                .filter(tag -> tag.getValue() != null)
                .collect(Collectors.toMap(
                        Tag::getKey,
                        Tag::getValue,
                    (oldValue, newValue) -> newValue));
    }

    /**
     * getPreviouslyAttachedTags
     * <p>
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get previous attached user defined tags from both handlerRequest.getPreviousResourceTags (stack tags)
     * and handlerRequest.getPreviousResourceState (resource tags).
     * @param handlerRequest The request to get tags from.
     * @return get previous tags
     */
    public static Map<String, String> getPreviouslyAttachedTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        // get previous stack level tags from handlerRequest
        final Map<String, String> previousTags = handlerRequest.getPreviousResourceTags() != null
                ? handlerRequest.getPreviousResourceTags() : new HashMap<>();

        if (handlerRequest.getPreviousResourceState() != null) {
            previousTags.putAll(convertToMap(handlerRequest.getPreviousResourceState().getTags()));
        }
        return previousTags;
    }

    /**
     * getNewDesiredTags
     * <p>
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get new user defined tags from both resource model and previous stack tags.
     * @param resourceModel The model to get tags from.
     * @param handlerRequest The request to get tags from.
     * @return get tags
     */
    public static Map<String, String> getNewDesiredTags(final ResourceModel resourceModel,
                                                        final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        // get new stack level tags from handlerRequest
        final Map<String, String> desiredTags = handlerRequest.getDesiredResourceTags() != null
                ? handlerRequest.getDesiredResourceTags() : new HashMap<>();

        if (resourceModel != null) {
            desiredTags.putAll(convertToMap(resourceModel.getTags()));
        }
        return desiredTags;
    }
}

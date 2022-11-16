package software.amazon.fsx.common.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import software.amazon.awssdk.services.fsx.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Tagging {
    static final Pattern TAG_KEY_PATTERN = Pattern.compile("^(^(?!(?i)aws:).[\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]*)$");
    static final Pattern TAG_VALUE_PATTERN = Pattern.compile("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]*)$");
    static final String VALIDATION_FAILURE_MESSAGE_FORMAT = "1 validation error detected: Value '%s' at "
            + "'tags.%s.member.%s' failed to satisfy constraint: Member must satisfy regular expression pattern: %s";
    static final String CLOUDFORMATION_AWS_SYSTEM_TAG_PREFIX = "aws:cloudformation";

    private Tagging() {
    }

    /**
     * Convert model tags to SDK tags.
     * @param tagSet The tags to convert.
     * @return The converted tags to use in the FSx SDK.
     */
    public static Collection<Tag> translateTagSetToSdk(final TagSet tagSet) {
        //For backward compatibility, We will resolve duplicates tags between stack level tags and resource tags, with
        //  resource tags taking the highest priority.
        final Map<String, Tag> allTags = new LinkedHashMap<>();
        addToMapIfAbsent(allTags, tagSet.getResourceTags());
        addToMapIfAbsent(allTags, tagSet.getStackTags());
        addToMapIfAbsent(allTags, tagSet.getSystemTags());
        return allTags.values();
    }

    /**
     * Check if each tag is in the map and add it if it isn't.
     * @param allTags The tags we want to add to.
     * @param tags The tags we want to add to the map.
     */
    private static void addToMapIfAbsent(final Map<String, Tag> allTags,
                                         final Collection<Tag> tags) {
        for (final Tag tag : tags) {
            allTags.putIfAbsent(tag.key(), tag);
        }
    }

    /**
     * Convert map of key value pairs to SDK tags.
     * @param tags The tags we want to convert to FSx SDK tags.
     * @return The set of the FSx SDK tags.
     */
    public static Set<Tag> translateTagsMapToSdk(final Map<String, String> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySortedMap()).entrySet()
                .stream()
                .map(entry -> Tag.builder()
                        .key(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Check if each tag follows the valid FSx format.
     * @param tags The tags to validate.
     * @throws CfnInvalidRequestException The exception we throw if the tags are invalid.
     */
    public static void validateTags(final Set<Tag> tags) throws CfnInvalidRequestException {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        int pointer = 0;
        for (final Tag tag : tags) {
            if (!TAG_KEY_PATTERN.matcher(tag.key()).matches()) {
                throw new CfnInvalidRequestException(buildValidationFailureMessage(true, pointer, tag.key()));
            }
            if (!TAG_VALUE_PATTERN.matcher(tag.value()).matches()) {
                throw new CfnInvalidRequestException(buildValidationFailureMessage(false, pointer, tag.key()));
            }
            pointer++;
        }
    }

    /**
     * Gets all tags that are not related to the cloudformation stack.
     * @param tags All tags.
     * @return All tags not related to cloudformation stack.
     */
    public static Set<Tag> getAllNonCloudFormationAwsPrefixedKeys(final Set<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .filter(tag -> !tag.key().startsWith(CLOUDFORMATION_AWS_SYSTEM_TAG_PREFIX))
                .collect(Collectors.toSet());
    }

    /**
     * The error message to build if a tag is invalid.
     * @param isKey The variable used to switch between key and value.
     * @param index The index for the failing key.
     * @param invalidString The value of the failing key/value in the tag.
     * @return The error message.
     */
    private static String buildValidationFailureMessage(final boolean isKey,
                                                        final int index,
                                                        final String invalidString) {
        return String.format(VALIDATION_FAILURE_MESSAGE_FORMAT, invalidString, index, isKey ? "key" : "value",
                isKey ? TAG_KEY_PATTERN : TAG_VALUE_PATTERN);
    }

    /**
     * generateTagsToAdd
     * <p>
     * Determines the tags the customer desired to define or redefine.
     * @param previousTags The old, desired tags
     * @param desiredTags The new, desired tags
     * @return generated tags
     */
    public static Map<String, String> generateTagsToAdd(final Map<String, String> previousTags,
                                                        final Map<String, String> desiredTags) {
        return desiredTags.entrySet().stream()
                .filter(e -> !previousTags.containsKey(e.getKey()) || !Objects.equals(previousTags.get(e.getKey()), e.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
    }

    /**
     * getTagsToRemove
     * <p>
     * Determines the tags the customer desired to remove from the function.
     * @param previousTags The old, desired tags
     * @param desiredTags The new, desired tags
     * @return tags
     */
    public static Set<String> generateTagsToRemove(final Map<String, String> previousTags,
                                                   final Map<String, String> desiredTags) {
        final Set<String> desiredTagNames = desiredTags.keySet();

        return previousTags.keySet().stream()
                .filter(tagName -> !desiredTagNames.contains(tagName))
                .collect(Collectors.toSet());
    }

    @Builder(toBuilder = true)
    @AllArgsConstructor
    @Data
    public static class TagSet {
        @Builder.Default
        private Set<Tag> resourceTags = new LinkedHashSet<>();
        @Builder.Default
        private Set<Tag> stackTags = new LinkedHashSet<>();
        @Builder.Default
        private Set<Tag> systemTags = new LinkedHashSet<>();

        /**
         * Determines if the structure is empty.
         * @return If the structure is empty.
         */
        public boolean isEmpty() {
            return resourceTags.isEmpty()
                    && stackTags.isEmpty()
                    && systemTags.isEmpty();
        }
    }
}

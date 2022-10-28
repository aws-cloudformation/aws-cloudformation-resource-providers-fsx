package software.amazon.fsx.common.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.fsx.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.HandlerErrorCode;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.fsx.common.handler.Tagging.CLOUDFORMATION_AWS_SYSTEM_TAG_PREFIX;
import static software.amazon.fsx.common.handler.Tagging.TAG_KEY_PATTERN;
import static software.amazon.fsx.common.handler.Tagging.TAG_VALUE_PATTERN;
import static software.amazon.fsx.common.handler.Tagging.VALIDATION_FAILURE_MESSAGE_FORMAT;

@ExtendWith(MockitoExtension.class)
public class TaggingTest {

    private Tag tag1;
    private Tag tag2;
    private Tag tag3;
    private Tag tag1DuplicateKey;
    private Tag tagWithInvalidKey;
    private Tag tagWithInvalidValue;

    @BeforeEach
    public void setup() {
        tag1 = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "scientist")
                .build();
        tag2 = Tag.builder()
                .key(/*key*/ "key2")
                .value(/*value*/ "cactus")
                .build();
        tag3 = Tag.builder()
                .key(/*key*/ "key3")
                .value(/*value*/ "moonlight")
                .build();
        tag1DuplicateKey = Tag.builder()
                .key(tag1.key())
                .value(/*value*/ "overwrittenValue")
                .build();
        tagWithInvalidKey = Tag.builder()
                .key(/*key*/ "aws:dtna")
                .value(/*value*/ "overwrittenValue")
                .build();

        tagWithInvalidValue = Tag.builder()
                .key(/*key*/ "key4")
                .value(/*value*/ "¡™£¢∞§¶•ªº")
                .build();
    }

    @Test
    public void testTranslateTagSetToSdk() {
        final List<Tag> expectedTags = Arrays.asList(tag1,
                tag2,
                tag3);

        final Set<Tag> resourceTags = new HashSet<>(Collections.singletonList(tag1));
        final Set<Tag> stackTags = new HashSet<>(Collections.singletonList(tag2));
        final Set<Tag> systemTags = new HashSet<>(Arrays.asList(tag3, tag1DuplicateKey));

        final Tagging.TagSet allTags = Tagging.TagSet.builder()
                .resourceTags(resourceTags)
                .stackTags(stackTags)
                .systemTags(systemTags)
                .build();

        final Collection<Tag> convertedTags = Tagging.translateTagSetToSdk(allTags);

        assertThat(convertedTags.size()).isEqualTo(expectedTags.size());
        convertedTags.forEach(convertedTag ->
                assertThat(expectedTags.contains(convertedTag)).isTrue());
    }

    @Test
    public void testTranslateTagsMapToSdk() {
        final Set<Tag> expectedTags = new HashSet<>(Arrays.asList(tag1,
                tag2,
                tag3));

        final Map<String, String> tags = new HashMap<>();
        tags.put(tag1.key(), tag1.value());
        tags.put(tag2.key(), tag2.value());
        tags.put(tag3.key(), tag3.value());

        final Collection<Tag> convertedTags = Tagging.translateTagsMapToSdk(tags);

        assertThat(expectedTags).isEqualTo(convertedTags);
    }

    @Test
    public void testValidateTags_AllValid() {
        final Set<Tag> tagsToValidate = new HashSet<>(Arrays.asList(tag1,
                tag2,
                tag3));
        Tagging.validateTags(tagsToValidate);
    }

    @Test
    public void testValidateTags_NullTags() {
        Tagging.validateTags(null);
    }

    @Test
    public void testValidateTags_EmptyTags() {
        Tagging.validateTags( new HashSet<>());
    }

    @Test
    public void testValidateTags_InvalidKey() {
        final Set<Tag> tagsToValidate = new LinkedHashSet<Tag>();
        tagsToValidate.add(tag1);
        tagsToValidate.add(tag2);
        tagsToValidate.add(tagWithInvalidKey);

        assertThatThrownBy(() -> Tagging.validateTags(tagsToValidate))
                .isInstanceOf(CfnInvalidRequestException.class)
                .hasMessage(String.format(HandlerErrorCode.InvalidRequest.getMessage(),
                        String.format(VALIDATION_FAILURE_MESSAGE_FORMAT,
                                tagWithInvalidKey.key(),
                                /*index*/ 2,
                                /*member*/ "key",
                                TAG_KEY_PATTERN)));
    }

    @Test
    public void testValidateTags_InvalidValue() {
        final Set<Tag> tagsToValidate = new LinkedHashSet<Tag>();
        tagsToValidate.add(tag1);
        tagsToValidate.add(tag2);
        tagsToValidate.add(tagWithInvalidValue);

        assertThatThrownBy(() -> Tagging.validateTags(tagsToValidate))
                .isInstanceOf(CfnInvalidRequestException.class)
                .hasMessage(String.format(HandlerErrorCode.InvalidRequest.getMessage(),
                        String.format(VALIDATION_FAILURE_MESSAGE_FORMAT,
                                tagWithInvalidValue.key(),
                                /*index*/ 2,
                                /*member*/ "value",
                                TAG_VALUE_PATTERN)));
    }

    @Test
    public void testTagSetIsEmpty() {
        final Set<Tag> resourceTags = new HashSet<>(Collections.singletonList(tag1));
        final Set<Tag> stackTags = new HashSet<>(Collections.singletonList(tag2));
        final Set<Tag> systemTags = new HashSet<>(Arrays.asList(tag3, tag1DuplicateKey));

        Tagging.TagSet allTags = Tagging.TagSet.builder().build();
        assertThat(allTags.isEmpty()).isTrue();

        allTags = Tagging.TagSet.builder()
                .resourceTags(resourceTags)
                .build();
        assertThat(allTags.isEmpty()).isFalse();

        allTags = Tagging.TagSet.builder()
                .stackTags(stackTags)
                .build();
        assertThat(allTags.isEmpty()).isFalse();

        allTags = Tagging.TagSet.builder()
                .systemTags(systemTags)
                .build();
        assertThat(allTags.isEmpty()).isFalse();

        allTags = Tagging.TagSet.builder()
                .resourceTags(resourceTags)
                .stackTags(stackTags)
                .systemTags(systemTags)
                .build();
        assertThat(allTags.isEmpty()).isFalse();
    }

    @Test
    public void testGenerateTagsToAdd() {
        final Map<String, String> previousTags = new HashMap<>();
        previousTags.put(tag1.key(), tag1.value());
        previousTags.put(tag2.key(), tag2.value());

        final Map<String, String> desiredTags = new HashMap<>();
        desiredTags.put(tag1DuplicateKey.key(), tag1DuplicateKey.value());
        desiredTags.put(tag2.key(), tag2.value());
        desiredTags.put(tag3.key(), tag3.value());

        final Map<String, String> tags = Tagging.generateTagsToAdd(previousTags, desiredTags);
        assertThat(tags.size()).isEqualTo(2);
        assertThat(tags.get(tag3.key())).isEqualTo(tag3.value());
        assertThat(tags.get(tag1DuplicateKey.key())).isEqualTo(tag1DuplicateKey.value());
    }

    @Test
    public void testGenerateTagsToRemove() {
        final Map<String, String> previousTags = new HashMap<>();
        previousTags.put(tag1.key(), tag1.value());
        previousTags.put(tag2.key(), tag2.value());
        previousTags.put(tag3.key(), tag3.value());

        final Map<String, String> desiredTags = new HashMap<>();
        desiredTags.put(tag1.key(), tag1.value());

        final Set<String> tags = Tagging.generateTagsToRemove(previousTags, desiredTags);
        assertThat(tags.size()).isEqualTo(2);
        assertThat(tags.contains(tag2.key())).isTrue();
        assertThat(tags.contains(tag3.key())).isTrue();
    }

    @Test
    public void testGetAllNonCloudFormationAwsPrefixedKeys_EmptyList() {
        final Set<Tag> actualTags = Tagging.getAllNonCloudFormationAwsPrefixedKeys(new HashSet<>());
        assertThat(actualTags.isEmpty()).isTrue();
    }

    @Test
    public void testGetAllNonCloudFormationAwsPrefixedKeys() {
        final Tag cloudformationTag = Tag.builder()
                .key(/*key*/ CLOUDFORMATION_AWS_SYSTEM_TAG_PREFIX + "key3")
                .value(/*value*/ "value3")
                .build();

        final Set<Tag> allTags = new HashSet<>();
        allTags.add(tag1);
        allTags.add(tag2);
        allTags.add(cloudformationTag);

        final Set<Tag> expectedTags = new HashSet<>();
        expectedTags.add(tag1);
        expectedTags.add(tag2);

        final Set<Tag> actualTags = Tagging.getAllNonCloudFormationAwsPrefixedKeys(allTags);
        assertThat(actualTags).isEqualTo(expectedTags);
    }
}

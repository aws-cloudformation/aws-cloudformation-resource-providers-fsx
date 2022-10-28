package software.amazon.fsx.datarepositoryassociation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TagHelperTest {

    @Test
    public void testConvertToMap_EmptyCollection() {
        final List<Tag> tags = new ArrayList<>();
        final Map<String, String> mapOfTags = TagHelper.convertToMap(tags);
        assertThat(mapOfTags.isEmpty()).isTrue();
    }

    @Test
    public void testConvertToMap_NullValue() {
        final Tag tagToConvert = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ null)
                .build();
        final List<Tag> tags = new ArrayList<>();
        tags.add(tagToConvert);

        final Map<String, String> mapOfTags = TagHelper.convertToMap(tags);
        assertThat(mapOfTags.isEmpty()).isTrue();
    }

    @Test
    public void testConvertToMap_HappyPath() {
        final Tag oldTagToConvert = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "value1")
                .build();
        final Tag newTagToConvert = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "value2")
                .build();
        final List<Tag> tags = new ArrayList<>();
        tags.add(oldTagToConvert);
        tags.add(newTagToConvert);

        final Map<String, String> mapOfTags = TagHelper.convertToMap(tags);
        assertThat(mapOfTags.size()).isEqualTo(1);
        assertThat(mapOfTags.get(newTagToConvert.getKey())).isEqualTo(newTagToConvert.getValue());
    }

    @Test
    public void testGetPreviouslyAttachedTags_NullTags() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceTags(/*previousResourceTags*/ null)
                .previousResourceState(/*previousResourceState*/ null)
                .build();

        final Map<String, String> tags = TagHelper.getPreviouslyAttachedTags(request);
        assertThat(tags.isEmpty()).isTrue();
    }

    @Test
    public void testGetPreviouslyAttachedTags() {
        final Tag tag1 = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "value1")
                .build();
        final Tag tag2 = Tag.builder()
                .key(/*key*/ "key2")
                .value(/*value*/ "value2")
                .build();
        final Map<String, String> previousResourceTags = new HashMap<>();
        previousResourceTags.put(tag1.getKey(), tag1.getValue());


        final ResourceModel previousResourceState = ResourceModel.builder()
                .tags(Collections.singletonList(tag2))
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceTags(previousResourceTags)
                .previousResourceState(previousResourceState)
                .build();

        final Map<String, String> tags = TagHelper.getPreviouslyAttachedTags(request);
        assertThat(tags.size()).isEqualTo(2);
        assertThat(tags.get(tag1.getKey())).isEqualTo(tag1.getValue());
        assertThat(tags.get(tag2.getKey())).isEqualTo(tag2.getValue());
    }

    @Test
    public void testGetNewDesiredTags_NullTags() {
        final ResourceModel desiredResourceState = ResourceModel.builder()
            .tags(/*tags*/ null)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceTags(/*desiredResourceTags*/ null)
                .desiredResourceState(desiredResourceState)
                .build();

        final Map<String, String> tags = TagHelper.getNewDesiredTags(/*resourceModel*/ null, request);
        assertThat(tags.isEmpty()).isTrue();
    }

    @Test
    public void testGetNewDesiredTags() {
        final Tag tag1 = Tag.builder()
                .key(/*key*/ "key1")
                .value(/*value*/ "value1")
                .build();
        final Tag tag2 = Tag.builder()
                .key(/*key*/ "key2")
                .value(/*value*/ "value2")
                .build();
        final Map<String, String> desiredResourceTags = new HashMap<>();
        desiredResourceTags.put(tag1.getKey(), tag1.getValue());


        final ResourceModel desiredResourceState = ResourceModel.builder()
                .tags(Collections.singletonList(tag2))
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceTags(desiredResourceTags)
                .desiredResourceState(desiredResourceState)
                .build();

        final Map<String, String> tags = TagHelper.getNewDesiredTags(desiredResourceState, request);
        assertThat(tags.size()).isEqualTo(2);
        assertThat(tags.get(tag1.getKey())).isEqualTo(tag1.getValue());
        assertThat(tags.get(tag2.getKey())).isEqualTo(tag2.getValue());
    }
}

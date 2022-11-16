package software.amazon.fsx.datarepositoryassociation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.fsx.FSxClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ClientBuilderTest {
    @Test
    public void testGetClient() {
        final FSxClient client = ClientBuilder.getClient();
        assertThat(client).isNotNull();
    }
}

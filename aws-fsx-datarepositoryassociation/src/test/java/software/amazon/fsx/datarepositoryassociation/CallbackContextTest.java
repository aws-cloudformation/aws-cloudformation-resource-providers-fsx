package software.amazon.fsx.datarepositoryassociation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class CallbackContextTest {
    //Just for coverage
    @Test
    public void testConstructor() {
        final CallbackContext callbackContext = new CallbackContext();
        assertThat(callbackContext).isNotNull();
    }
}

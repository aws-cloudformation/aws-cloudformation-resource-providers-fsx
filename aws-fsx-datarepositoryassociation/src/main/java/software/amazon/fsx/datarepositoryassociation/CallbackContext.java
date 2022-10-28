package software.amazon.fsx.datarepositoryassociation;

import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.Builder(toBuilder = true)
public class CallbackContext extends StdCallbackContext {
    public CallbackContext() {
        super();
    }
}

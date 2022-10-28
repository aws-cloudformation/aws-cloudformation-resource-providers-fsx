package software.amazon.fsx.datarepositoryassociation;

import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.cloudformation.LambdaWrapper;

public final class ClientBuilder {
    private ClientBuilder() {
    }

    public static FSxClient getClient() {
        return FSxClient.builder()
                .httpClient(LambdaWrapper.HTTP_CLIENT)
                .build();
    }
}

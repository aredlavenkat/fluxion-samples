package ai.fluxion.sample.connectors;

import ai.fluxion.core.engine.connectors.ConnectorOption;
import ai.fluxion.core.engine.connectors.SinkConnectorProvider;
import ai.fluxion.core.engine.connectors.SourceConnectorDescriptor;
import ai.fluxion.core.engine.connectors.SourceConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceLoader;

public final class ConnectorsCatalogApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorsCatalogApp.class);

    public static void main(String[] args) {
        listSourceConnectors();
        listSinkConnectors();
    }

    private static void listSourceConnectors() {
        LOGGER.info("--- Source Connectors ---");
        ServiceLoader<SourceConnectorProvider> loader = ServiceLoader.load(SourceConnectorProvider.class);
        for (SourceConnectorProvider provider : loader) {
            SourceConnectorDescriptor descriptor = provider.descriptor();
            LOGGER.info("{} ({}) - {}", descriptor.displayName(), descriptor.type(), descriptor.description());
            printOptions(provider.options());
        }
    }

    private static void listSinkConnectors() {
        LOGGER.info("--- Sink Connectors ---");
        ServiceLoader<SinkConnectorProvider> loader = ServiceLoader.load(SinkConnectorProvider.class);
        for (SinkConnectorProvider provider : loader) {
            LOGGER.info("{} - type={}", provider.getClass().getSimpleName(), provider.type());
        }
    }

    private static void printOptions(List<ConnectorOption> options) {
        if (options == null || options.isEmpty()) {
            LOGGER.info("  (no declarative options)");
            return;
        }
        for (ConnectorOption option : options) {
            LOGGER.info("  - {} [{}] required={} default={} secret={} :: {}",
                    option.name(),
                    option.type(),
                    option.required(),
                    option.defaultValue(),
                    option.secret(),
                    option.description());
        }
    }
}

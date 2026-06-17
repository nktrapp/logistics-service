package br.furb.logistics.application.aot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AOT bean-graph contract (native pruning guard)")
class AotBeanGraphContractTest {

    private static final Path AOT_SOURCES = Path.of(
            System.getProperty("aot.sources.dir", "build/generated/aotSources"));

    @Test
    @DisplayName("processAot keeps every bean whose existence a runtime condition could silently prune")
    void aotGraphContainsCriticalBeans() throws IOException {
        assertThat(AOT_SOURCES)
                .as("processAot output not found at %s — run ':application:processAot' before this test", AOT_SOURCES)
                .isDirectory();

        List<String> generatedFiles = generatedFileNames();

        assertThat(generatedFiles)
                .as("MapStruct mappers must remain Spring beans frozen into the native graph")
                .contains(
                        "HubMapperImpl__BeanDefinitions.java",
                        "RouteMapperImpl__BeanDefinitions.java",
                        "HubConnectionMapperImpl__BeanDefinitions.java");

        assertThat(generatedFiles)
                .as("SQS listeners, the hub seeder and the use-case wiring must survive AOT")
                .contains(
                        "HubEventListenerAdapter__BeanDefinitions.java",
                        "LogisticsEventListenerAdapter__BeanDefinitions.java",
                        "HubDataSeeder__BeanDefinitions.java",
                        "UseCaseConfig__BeanDefinitions.java");

        assertThat(generatedFiles)
                .as("the customized OpenAPI bean must survive AOT so the spec is available in the native image")
                .contains("OpenApiConfig__BeanDefinitions.java");

        assertThat(containsOtlpSpanExporter())
                .as("OtlpHttpSpanExporter must be registered — a property default of false silently prunes it from the image (regression F-03)")
                .isTrue();
    }

    private List<String> generatedFileNames() throws IOException {
        try (Stream<Path> files = Files.walk(AOT_SOURCES)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    private boolean containsOtlpSpanExporter() throws IOException {
        try (Stream<Path> files = Files.walk(AOT_SOURCES)) {
            return files.filter(Files::isRegularFile)
                    .anyMatch(AotBeanGraphContractTest::registersOtlpSpanExporter);
        }
    }

    private static boolean registersOtlpSpanExporter(Path path) {
        try {
            return Files.readString(path).contains("RootBeanDefinition(OtlpHttpSpanExporter.class)");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

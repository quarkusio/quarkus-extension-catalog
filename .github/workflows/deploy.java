///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.1
//DEPS https://github.com/gastaldi/quarkus-registry-generator/tree/1.0.0.Alpha1
//JAVA_OPTIONS "-Djava.util.logging.SimpleFormatter.format=%1$s [%4$s] %5$s%6$s%n"
//JAVA 11

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonExtension;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.generator.RegistryGenerator;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "deploy", mixinStandardHelpOptions = true, version = "deploy 0.1",
        description = "deploy made with jbang")
class deploy implements Callable<Integer> {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private static final Logger log = Logger.getLogger(deploy.class);

    @Option(names = { "-w", "--working-directory" }, description = "The working directory", required = true)
    private Path workingDirectory;

    @Option(names = { "-o", "--output-directory" }, description = "The output directory", required = true)
    private Path outputDirectory;

    private final ObjectMapper yamlMapper;

    public static void main(String... args) {
        int exitCode = new CommandLine(new deploy()).execute(args);
        System.exit(exitCode);
    }

    public deploy() {
        this.yamlMapper = new YAMLMapper();
        JsonCatalogMapperHelper.initMapper(yamlMapper);
    }

    @Override
    public Integer call() throws Exception {
        try (RegistryGenerator registryGenerator = new RegistryGenerator(outputDirectory)) {
            list(workingDirectory.resolve("platforms"), registryGenerator, this::processCatalog);
            list(workingDirectory.resolve("extensions"), registryGenerator, this::processExtension);
        }
        return 0;
    }

    private void list(Path path, RegistryGenerator registryGenerator, BiConsumer<Path, RegistryGenerator> consumer)
            throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            files
                    .filter(file -> file.getFileName().toString().endsWith(".yaml"))
                    .forEach((c) -> consumer.accept(c, registryGenerator));
        }
    }

    private void processCatalog(Path platformYaml, RegistryGenerator registryGenerator) {
        try {
            log.infof("Processing platform %s", platformYaml);
            log.info("---------------------------------------------------------------");
            ObjectNode tree = (ObjectNode) yamlMapper.readTree(platformYaml.toFile());
            if (!tree.path("enabled").asBoolean(true)) {
                log.info("Platform is disabled. Skipping");
                return;
            }
            String repository = tree.path("maven-repository").asText(MAVEN_CENTRAL);
            String groupId = tree.get("group-id").asText();
            String artifactId = tree.get("artifact-id").asText();
            // Process all platforms
            for (JsonNode node : tree.withArray("versions")) {
                String version = node.asText();
                String classifier = tree.path("classifier").asText();
                if (tree.path("classifier-as-version").asBoolean()) {
                    classifier = version;
                }
                // Get Extension YAML
                JsonExtensionCatalog jsonPlatform = readCatalog(repository, groupId, artifactId, version, classifier);
                registryGenerator.add(jsonPlatform);
            }
        } catch (IOException e) {
            log.error("Error while processing platform", e);
        }
        log.info("---------------------------------------------------------------");

    }

    private void processExtension(Path extensionYaml, RegistryGenerator registryGenerator) {
        try {
            log.infof("Processing extension %s", extensionYaml);
            log.info("---------------------------------------------------------------");
            // Read
            ObjectNode tree = (ObjectNode) yamlMapper.readTree(extensionYaml.toFile());
            if (!tree.path("enabled").asBoolean(true)) {
                log.info("Extension is disabled. Skipping");
                log.info("---------------------------------------------------------------");
                return;
            }
            String repository = tree.path("maven-repository").asText(MAVEN_CENTRAL);
            String groupId = tree.get("group-id").asText();
            String artifactId = tree.get("artifact-id").asText();
            log.infof("Fetching latest version for %s:%s", groupId, artifactId);
            ArrayNode versionsNode = tree.withArray("versions");
            if (versionsNode.isEmpty()) {
                log.info("No versions found. Skipping");
                log.info("---------------------------------------------------------------");
                return;
            }
            // Get Latest Version
            String latestVersion = versionsNode.get(0).asText();
            // Get Extension YAML
            JsonExtension jsonExtension = readExtension(repository, groupId, artifactId, latestVersion);
            registryGenerator.add(jsonExtension);
        } catch (IOException e) {
            log.error("Error while processing extension", e);
        }
        log.info("---------------------------------------------------------------");
    }

    private JsonExtensionCatalog readCatalog(String repository, String groupId, String artifactId, String version,
            String classifier)
            throws IOException {
        URI platformJson;
        if (classifier == null) {
            platformJson = URI.create(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{3}.json",
                    Objects.toString(repository, MAVEN_CENTRAL),
                    groupId.replace('.', '/'),
                    artifactId,
                    version));
        } else {
            //            https://repo1.maven.org/maven2/io/quarkus/quarkus-bom-quarkus-platform-descriptor/1.13.0.Final/quarkus-bom-quarkus-platform-descriptor-1.13.0.Final-1.13.0.Final.json
            platformJson = URI.create(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{4}-{3}.json",
                    Objects.toString(repository, MAVEN_CENTRAL),
                    groupId.replace('.', '/'),
                    artifactId,
                    version,
                    classifier));
        }
        try (CloseableHttpClient httpClient = createHttpClient();
                InputStream is = httpClient.execute(new HttpGet(platformJson)).getEntity().getContent()) {
            return JsonCatalogMapperHelper.deserialize(is, JsonExtensionCatalog.class);
        }
    }

    private JsonExtension readExtension(String repository, String groupId, String artifactId, String version)
            throws IOException {
        URL extensionJarURL = new URL(MessageFormat.format("jar:{0}{1}/{2}/{3}/{2}-{3}.jar!/META-INF/quarkus-extension.yaml",
                Objects.toString(repository, MAVEN_CENTRAL),
                groupId.replace('.', '/'),
                artifactId,
                version));
        try (InputStream is = extensionJarURL.openStream()) {
            return JsonCatalogMapperHelper.deserialize(yamlMapper, is, JsonExtension.class);
        }
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }
}

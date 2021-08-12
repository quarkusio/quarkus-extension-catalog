///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.1
//DEPS io.quarkus:quarkus-devtools-registry-client:2.1.2.Final
//JAVA_OPTIONS "-Djava.util.logging.SimpleFormatter.format=%1$s [%4$s] %5$s%6$s%n"
//JAVA 11

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "publishcatalog", mixinStandardHelpOptions = true, version = "publishcatalog 0.1",
        description = "publishcatalog made with jbang")
class publish implements Callable<Integer> {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private static final Logger log = Logger.getLogger(publish.class);

    @Option(names = { "-w", "--working-directory" }, description = "The working directory", required = true)
    Path workingDirectory;

    @Option(names = { "-u",
            "--registry-url" }, description = "The Extension Registry URL", required = true, defaultValue = "${REGISTRY_URL}")
    URI registryURL;

    @Option(names = { "-t",
            "--token" }, description = "The token to use when authenticating to the admin endpoint", defaultValue = "${REGISTRY_TOKEN}")
    String token;

    @Option(names = {"-a", "--all"}, description = "Publish all versions? If false, just the latest is published")
    boolean all;

    private final ObjectMapper yamlMapper;

    public static void main(String... args) {
        int exitCode = new CommandLine(new publish()).execute(args);
        System.exit(exitCode);
    }

    public publish() {
        this.yamlMapper = new YAMLMapper();
        JsonCatalogMapperHelper.initMapper(yamlMapper);
    }

    @Override
    public Integer call() throws Exception {
        list(workingDirectory.resolve("platforms"), this::processCatalog);
        list(workingDirectory.resolve("extensions"), this::processExtension);
        return 0;
    }

    private void list(Path path, Consumer<Path> consumer) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            files.filter(file -> file.getFileName().toString().endsWith(".yaml"))
                    .forEach(consumer);
        }
    }

    private void processCatalog(Path platformYaml) {
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
            String platformKey = tree.get("platform-key").asText();
            String classifier = tree.path("classifier").asText();
            boolean classifierAsVersion = tree.path("classifier-as-version").asBoolean();
            for (JsonNode node : tree.withArray("versions")) {
                String version = node.asText();
                if (classifierAsVersion) {
                    classifier = version;
                }
                // Get Extension YAML
                byte[] jsonPlatform = readCatalog(repository, groupId, artifactId, version, classifier);
                // Publish
                log.infof("Publishing %s:%s:%s", groupId, artifactId, version);
                publishCatalog(platformKey, jsonPlatform);
                if (!all) {
                    // Just publish the first one
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Error while processing platform", e);
        }
        log.info("---------------------------------------------------------------");

    }

    private void processExtension(Path extensionYaml) {
        try {
            log.infof("Processing extension %s", extensionYaml);
            log.info("---------------------------------------------------------------");
            // Read
            ObjectNode tree = (ObjectNode) yamlMapper.readTree(extensionYaml.toFile());
            if (!tree.path("enabled").asBoolean(true)) {
                log.info("Extension is disabled. Skipping");
                return;
            }
            String repository = tree.path("maven-repository").asText(MAVEN_CENTRAL);
            String groupId = tree.get("group-id").asText();
            String artifactId = tree.get("artifact-id").asText();
            for (JsonNode node : tree.withArray("versions")) {
                String version = node.asText();
                // Get Extension YAML
                byte[] jsonExtension = readExtension(repository, groupId, artifactId, version);
                // Publish
                log.infof("Publishing %s:%s:%s", groupId, artifactId, version);
                publishExtension(jsonExtension);
                if (!all) {
                    // Just publish the first one
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Error while processing extension", e);
        }
        log.info("---------------------------------------------------------------");
    }

    private byte[] readCatalog(String repository, String groupId, String artifactId, String version, String classifier)
            throws IOException {
        URI platformJson;
        if (classifier == null) {
            platformJson = URI.create(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{3}.json",
                    Objects.toString(repository, MAVEN_CENTRAL),
                    groupId.replace('.', '/'),
                    artifactId,
                    version));
        } else {
            // https://repo1.maven.org/maven2/io/quarkus/quarkus-bom-quarkus-platform-descriptor/1.13.0.Final/quarkus-bom-quarkus-platform-descriptor-1.13.0.Final-1.13.0.Final.json
            platformJson = URI.create(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{4}-{3}.json",
                    Objects.toString(repository, MAVEN_CENTRAL),
                    groupId.replace('.', '/'),
                    artifactId,
                    version,
                    classifier));
        }
        try (CloseableHttpClient httpClient = createHttpClient();
                InputStream is = httpClient.execute(new HttpGet(platformJson)).getEntity().getContent()) {
            return is.readAllBytes();
        }
    }

    private byte[] readExtension(String repository, String groupId, String artifactId, String version) throws IOException {
        URL extensionJarURL = new URL(MessageFormat.format("jar:{0}{1}/{2}/{3}/{2}-{3}.jar!/META-INF/quarkus-extension.yaml",
                Objects.toString(repository, MAVEN_CENTRAL),
                groupId.replace('.', '/'),
                artifactId,
                version));
        try (InputStream is = extensionJarURL.openStream()) {
            return is.readAllBytes();
        }
    }

    private void publishExtension(byte[] extension) throws IOException {
        try (final CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost post = new HttpPost(registryURL.resolve("/admin/v1/extension"));
            post.setHeader("Content-Type", "application/yaml");
            if (token != null) {
                post.setHeader("Token", token);
            }
            post.setEntity(new ByteArrayEntity(extension));
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == HttpURLConnection.HTTP_CONFLICT) {
                    log.info("Conflict, version already exists. Ignoring");
                    return;
                }
                if (statusLine.getStatusCode() != HttpURLConnection.HTTP_ACCEPTED) {
                    throw new IOException(statusLine.getStatusCode() + " -> " + statusLine.getReasonPhrase());
                } else {
                    log.info("Extension published");
                }
            }
        }
    }

    private void publishCatalog(String platformKey, byte[] jsonPlatform) throws IOException {
        try (final CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost post = new HttpPost(registryURL.resolve("/admin/v1/extension/catalog"));
            post.setHeader("X-Platform", platformKey);
            post.setHeader("Content-Type", "application/json");
            if (token != null) {
                post.setHeader("Token", token);
            }
            post.setEntity(new ByteArrayEntity(jsonPlatform));
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == HttpURLConnection.HTTP_CONFLICT) {
                    log.info("Conflict, version already exists. Ignoring");
                    return;
                }
                if (statusLine.getStatusCode() != HttpURLConnection.HTTP_ACCEPTED) {
                    throw new IOException(statusLine.getStatusCode() + " -> " + statusLine.getReasonPhrase());
                } else {
                    log.info("Platform published");
                }
            }
        }
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }
}

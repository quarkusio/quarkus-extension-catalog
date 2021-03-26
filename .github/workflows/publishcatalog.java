///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.1
//DEPS io.quarkus:quarkus-devtools-registry-client:1.13.0.Final

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "publishcatalog", mixinStandardHelpOptions = true, version = "publishcatalog 0.1",
        description = "publishcatalog made with jbang")
class publishcatalog implements Callable<Integer> {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private static final Logger log = Logger.getLogger(publishcatalog.class);

    @Parameters(index = "0", description = "The working directory")
    private Path workingDirectory;

    @Parameters(index = "1", description = "The admin endpoint URL", defaultValue = "https://registry.quarkus.io")
    private URI adminEndpoint;

    @Parameters(index = "2", description = "The password to use")
    private String password;

    private final ObjectMapper yamlMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static void main(String... args) {
        int exitCode = new CommandLine(new publishcatalog()).execute(args);
        System.exit(exitCode);
    }

    public publishcatalog() {
        this.yamlMapper = new YAMLMapper();
        JsonCatalogMapperHelper.initMapper(yamlMapper);
    }

    @Override
    public Integer call() throws Exception {
        processExtensions(workingDirectory.resolve("extensions"));
        //processPlatforms(workingDirectory.resolve("platforms"));
        return 0;
    }

    private void processExtensions(Path path) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            files.forEach(this::processExtension);
        }
    }

    private void processExtension(Path extensionJson) {
        try {
            // Read
            ObjectNode tree = (ObjectNode) yamlMapper.readTree(extensionJson.toFile());
            JsonNode repositoryNode = tree.get("repository");
            String repository = (repositoryNode == null) ? MAVEN_CENTRAL :
                    repositoryNode.asText();
            String groupId = tree.get("group-id").asText();
            String artifactId = tree.get("artifact-id").asText();
            log.infof("Fetching latest version for %s:%s", groupId, artifactId);
            ArrayNode versionsNode = tree.withArray("versions");
            // Get Latest Version
            String latestVersion = getLatestVersion(repository, groupId, artifactId);
            // Compare if not already in descriptor
            if (containsValue(versionsNode, latestVersion)) {
                log.infof("%s:%s version %s was read previously. Skipping", groupId, artifactId, latestVersion);
                return;
            }
            versionsNode.add(latestVersion);
            // Get Extension YAML
            byte[] jsonExtension = readExtension(repository, groupId, artifactId, latestVersion);
            // Publish
            log.infof("Publishing %s:%s:%s", groupId, artifactId, latestVersion);
            publishExtension(jsonExtension);
            // Write version
            yamlMapper.writeValue(extensionJson.toFile(), tree);
            // Git commit
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean containsValue(ArrayNode versionsNode, String latestVersion) {
        for (JsonNode node : versionsNode) {
            if (latestVersion.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private String getLatestVersion(String repository, String groupId, String artifactId) throws IOException {
        URL metadataURL = new URL(MessageFormat.format("{0}{1}/{2}/maven-metadata.xml",
                                                       Objects.toString(repository, MAVEN_CENTRAL),
                                                       groupId.replace('.', '/'),
                                                       artifactId));
        try (InputStream is = metadataURL.openStream()) {
            MetadataXpp3Reader metadataReader = new MetadataXpp3Reader();
            Metadata metadata = metadataReader.read(is);
            return metadata.getVersioning().getLatest();
        } catch (XmlPullParserException e) {
            throw new IOException("Invalid Metadata", e);
        }
    }

    private byte[] readExtension(String repository, String groupId, String artifactId, String version) throws IOException {
        URL extensionJarURL = getExtensionJarURL(repository, groupId, artifactId, version);
        try (InputStream is = extensionJarURL.openStream()) {
            return is.readAllBytes();
//                              JsonCatalogMapperHelper.deserialize(yamlMapper, is, JsonExtension.class);
        }
    }

    private URL getExtensionJarURL(String repository, String groupId, String artifactId, String version) {
        try {
            return new URL(MessageFormat.format("jar:{0}{1}/{2}/{3}/{2}-{3}.jar!/META-INF/quarkus-extension.yaml",
                                                Objects.toString(repository, MAVEN_CENTRAL),
                                                groupId.replace('.', '/'),
                                                artifactId,
                                                version));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Error while building JSON URL", e);
        }
    }


    private void publishExtension(byte[] extension) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(adminEndpoint.resolve("/admin/v1/extension"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/yaml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(extension))
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
        if (response.statusCode() == 409) {
            log.info("Conflict, version already exists. Ignoring");
            return;
        }
        if (response.statusCode() != 200) {
            throw new IOException(response.statusCode() + " -> " + response.body());
        }
        log.info("OK");
    }
}

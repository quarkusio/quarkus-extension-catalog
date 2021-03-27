///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.1
//DEPS io.quarkus:quarkus-devtools-registry-client:1.13.0.Final
//DEPS org.eclipse.jgit:org.eclipse.jgit:5.11.0.202103091610-r

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "publishcatalog", mixinStandardHelpOptions = true, version = "publishcatalog 0.1",
        description = "publishcatalog made with jbang")
class publishcatalog implements Callable<Integer> {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private static final Logger log = Logger.getLogger(publishcatalog.class);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Option(names = {"-w", "--working-directory"}, description = "The working directory", required = true)
    private Path workingDirectory;

    @Option(names = {"-u", "--registry-url"}, description = "The Extension Registry URL", required = true, defaultValue = "${REGISTRY_URL}")
    private URI registryURL;

    @Option(names = {"-t", "--token"}, description = "The token to use when authenticating to the admin endpoint", required = true, defaultValue = "${REGISTRY_TOKEN}")
    private String token;

    private final ObjectMapper yamlMapper;

    private Git git;

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
        try (Git gitHandle = Git.open(workingDirectory.toFile())) {
            this.git = gitHandle;
            processExtensions(workingDirectory.resolve("extensions"));
            //processPlatforms(workingDirectory.resolve("platforms"));
        }
        return 0;
    }

    private void processExtensions(Path path) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            files.forEach(this::processExtension);
        }
    }

    private void processExtension(Path extensionJson) {
        try {
            log.info("---------------------------------------------------------------");
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
            versionsNode.insert(0, latestVersion);
            // Get Extension YAML
            byte[] jsonExtension = readExtension(repository, groupId, artifactId, latestVersion);
            // Publish
            log.infof("Publishing %s:%s:%s", groupId, artifactId, latestVersion);
            publishExtension(jsonExtension);
            // Write version
            yamlMapper.writeValue(extensionJson.toFile(), tree);
            // Git commit
            gitCommit(extensionJson, "Add " + latestVersion + " to " + workingDirectory.resolve(extensionJson).normalize());
        } catch (IOException e) {
            log.error("Error while processing extension ", e);
        }
        log.info("---------------------------------------------------------------");
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
                .uri(registryURL.resolve("/admin/v1/extension"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/yaml")
                .header("Token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(extension))
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
        if (response.statusCode() == HttpURLConnection.HTTP_CONFLICT) {
            log.info("Conflict, version already exists. Ignoring");
            return;
        }
        if (response.statusCode() != HttpURLConnection.HTTP_ACCEPTED) {
            throw new IOException(response.statusCode() + " -> " + response.body());
        } else {
            log.info("Extension published");
        }
    }

    public void gitCommit(Path file, String message) throws IOException {
        try {
            git.add().addFilepattern(workingDirectory.resolve(file).normalize().toString()).call();
            git.commit().setSign(false).setMessage(message).call();
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }
}

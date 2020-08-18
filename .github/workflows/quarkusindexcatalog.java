//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS io.quarkus:quarkus-platform-descriptor-resolver-json:1.7.0.Final
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.1

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.registry.RepositoryIndexer;
import io.quarkus.registry.builder.RegistryBuilder;
import io.quarkus.registry.catalog.model.Repository;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.descriptor.resolver.json.QuarkusJsonPlatformDescriptorResolver;
import io.quarkus.registry.catalog.model.Extension;
import io.quarkus.registry.catalog.model.Platform;
import io.quarkus.registry.catalog.spi.ArtifactResolver;
import io.quarkus.registry.model.Release;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.Callable;

@Command(name = "quarkusindexcatalog", mixinStandardHelpOptions = true, version = "quarkusindexcatalog 0.1",
        description = "Indexes a catalog repository to a format read by DefaultExtensionRegistry")
class quarkusindexcatalog implements Callable<Integer> {

    @Option(names = {"-p","--repository-path"}, description = "The repository path to index", required = true)
    private Path repositoryPath;

    @Option(names = {"-o","--output-file"}, description = "The output file", required = true)
    private File outputFile;

    public static void main(String... args) {
        int exitCode = new CommandLine(new quarkusindexcatalog()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
        Repository repository = Repository.parse(repositoryPath, mapper);
        RepositoryIndexer indexer = new RepositoryIndexer(new DefaultArtifactResolver());
        RegistryBuilder builder = new RegistryBuilder();
        indexer.index(repository, builder);
        // Make sure the parent directory exists
        File parentFile = outputFile.getAbsoluteFile().getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        mapper.writeValue(outputFile.getAbsoluteFile(), builder.build());
        return 0;
    }


    class DefaultArtifactResolver implements ArtifactResolver {

        private final ObjectMapper yamlReader;
        private final QuarkusJsonPlatformDescriptorResolver resolver;

        private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

        public DefaultArtifactResolver() {
            this.yamlReader = new ObjectMapper(new YAMLFactory())
                    .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
            this.resolver = QuarkusJsonPlatformDescriptorResolver.newInstance();
        }

        @Override
        public QuarkusPlatformDescriptor resolvePlatform(Platform platform, Release release) throws IOException {
            return resolver.resolveFromBom(platform.getGroupId(), platform.getArtifactId(), release.getVersion());
        }

        @Override
        public io.quarkus.dependencies.Extension resolveExtension(Extension extension, Release release) throws IOException {
            URL extensionJarURL = getExtensionJarURL(extension, release);
            try {
                return yamlReader.readValue(extensionJarURL, io.quarkus.dependencies.Extension.class);
            } catch (FileNotFoundException e) {
                // META-INF/quarkus-extension.yaml does not exist in JAR
                return new io.quarkus.dependencies.Extension(extension.getGroupId(), extension.getArtifactId(),
                                                             release.getVersion());
            }
        }

        URL getPlatformJSONURL(Platform platform, Release release) {
            try {
                return new URL(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{3}.json",
                                                    Objects.toString(release.getRepositoryURL(), MAVEN_CENTRAL),
                                                    platform.getGroupIdJson().replace('.', '/'),
                                                    platform.getArtifactIdJson(),
                                                    release.getVersion()));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Error while building JSON URL", e);
            }
        }

        URL getExtensionJarURL(Extension extension, Release release) {
            try {
                return new URL(MessageFormat.format("jar:{0}{1}/{2}/{3}/{2}-{3}.jar!/META-INF/quarkus-extension.yaml",
                                                    Objects.toString(release.getRepositoryURL(), MAVEN_CENTRAL),
                                                    extension.getGroupId().replace('.', '/'),
                                                    extension.getArtifactId(),
                                                    release.getVersion()));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Error while building JSON URL", e);
            }
        }
    }
}

package grails.boot.config

import grails.config.Settings
import groovy.transform.CompileStatic
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer
import org.grails.boot.support.GrailsApplicationPostProcessor
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import org.springframework.util.ClassUtils
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * A Grails configuration that scans for classes using the packages defined by the packages() method and creates the necessary
 * {@link grails.core.GrailsApplication} and {@link grails.plugins.GrailsPluginManager} beans
 * that constitute a Grails application.
 *
 * @see GrailsApplicationPostProcessor
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Configuration
@EnableWebMvc
class GrailsConfiguration implements ResourceLoaderAware {

    ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver()

    /**
     * @return A post processor that uses the {@link grails.plugins.GrailsPluginManager} to configure the {@link org.springframework.context.ApplicationContext}
     */
    @Bean
    GrailsApplicationPostProcessor grailsApplicationPostProcessor() {
        return new GrailsApplicationPostProcessor(classes() as Class[])
    }

    /**
     * @return The classes that constitute the Grails application
     */
    Collection<Class> classes() {
        def readerFactory = new CachingMetadataReaderFactory(resourcePatternResolver)
        def packages = packages()
        Collection<Class> classes = [] as Set
        for (pkg in packages) {
            if(pkg == null) continue
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(pkg.name) + Settings.CLASS_RESOURCE_PATTERN;

            classes.addAll scanUsingPattern(pattern, readerFactory)
        }

        // try the default package in case of a script without recursing into subpackages
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +  "*.class"
        classes.addAll scanUsingPattern(pattern, readerFactory)

        def classLoader = Thread.currentThread().contextClassLoader
        for(cls in AbstractGrailsArtefactTransformer.transformedClassNames) {
            try {
                classes << classLoader.loadClass(cls)
            } catch (ClassNotFoundException cnfe) {
                // ignore
            }
        }

        return classes
    }

    /**
     * @return The packages to scan
     */
    Collection<Package> packages() {
        [ getClass().package ]
    }

    @Override
    void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader)
    }

    private Collection<Class> scanUsingPattern(String pattern, CachingMetadataReaderFactory readerFactory) {
        def resources = this.resourcePatternResolver.getResources(pattern)
        def classLoader = Thread.currentThread().contextClassLoader
        Collection<Class> classes = []
        for (Resource res in resources) {
            // ignore closures / inner classes
            if(!res.filename.contains('$')) {
                def reader = readerFactory.getMetadataReader(res)
                def metadata = reader.annotationMetadata
                if (metadata.annotationTypes.any() { String annotation -> annotation.startsWith('grails.') }) {
                    classes << classLoader.loadClass(reader.classMetadata.className)
                }
            }
        }
        return classes
    }

}

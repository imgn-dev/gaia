import java.lang.module.ModuleDescriptor

// Both descriptors of the multi-release output provide the service.
for (path in ['target/classes/module-info.class', 'target/classes/META-INF/versions/21/module-info.class']) {
    def descriptor = new File(basedir, path).withInputStream { ModuleDescriptor.read(it) }
    assert descriptor.provides().collectEntries { [it.service(), it.providers()] } == ['it.services.Greeter': ['it.services.Hello']]
}

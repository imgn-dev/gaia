import java.lang.module.ModuleDescriptor

def descriptor = new File(basedir, 'target/classes/module-info.class').withInputStream { ModuleDescriptor.read(it) }
assert descriptor.provides().isEmpty()

def log = new File(basedir, 'build.log').text
assert log.contains('Skipping the registration of services')

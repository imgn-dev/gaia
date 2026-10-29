import java.lang.module.ModuleDescriptor

// The second build compiled nothing, so the descriptor is the one the first build patched, which the goal fixed.
def classes = new File(basedir, 'target/classes')
assert new File(classes, 'META-INF/services/it.services.Greeter').readLines().every { it.startsWith('#') }
def descriptor = new File(classes, 'module-info.class').withInputStream { ModuleDescriptor.read(it) }
assert descriptor.provides().isEmpty()

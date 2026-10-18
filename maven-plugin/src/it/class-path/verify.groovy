// Without module-info.class, the goal leaves the classes as they are: the class path reads META-INF/services.
assert !new File(basedir, 'target/classes/module-info.class').exists()
assert new File(basedir, 'target/classes/META-INF/services/it.services.Greeter').isFile()

def log = new File(basedir, 'build.log').text
assert log.contains(':register-services (default) @ class-path')
assert !log.contains('Registered the providers')

import java.lang.module.ModuleFinder

// Loads the built module in a layer of its own, as the module path would, and asks ServiceLoader for its providers.
def classes = new File(basedir, 'target/classes').toPath()
def boot = ModuleLayer.boot()
def configuration = boot.configuration().resolveAndBind(ModuleFinder.of(classes), ModuleFinder.of(), ['it.services'])
def layer = boot.defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader())
def greeter = layer.findLoader('it.services').loadClass('it.services.Greeter')
def greetings = ServiceLoader.load(layer, greeter).collect { it.greet() }
assert greetings == ['Hello']

def log = new File(basedir, 'build.log').text
assert log.contains('Registered the providers of it.services.Greeter in ')

package io.onedev.server.plugin.pack.helm;

import io.onedev.commons.loader.AbstractPluginModule;
import io.onedev.server.pack.PackService;
import io.onedev.server.pack.PackSupport;

/**
 * NOTE: Do not forget to rename moduleClass property defined in the pom if you've renamed this class.
 *
 */
public class HelmModule extends AbstractPluginModule {

	@Override
	protected void configure() {
		super.configure();

		bind(HelmPackService.class);
		contribute(PackService.class, HelmPackService.class);
		contribute(PackSupport.class, new HelmPackSupport());
	}

}

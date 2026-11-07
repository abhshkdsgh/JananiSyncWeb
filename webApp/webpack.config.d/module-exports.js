// A stale/mixed Kotlin module graph must fail production builds as well as development.
config.module = config.module || {};
config.module.parser = config.module.parser || {};
config.module.parser.javascript = {
    ...(config.module.parser.javascript || {}),
    exportsPresence: 'error'
};

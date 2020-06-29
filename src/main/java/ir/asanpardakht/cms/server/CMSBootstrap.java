package ir.asanpardakht.cms.server;

import com.typesafe.config.Config;
import ir.asanpardakht.cms.common.Configs;
import ir.asanpardakht.cms.common.Env;
import ir.asanpardakht.cms.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CMSBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(CMSBootstrap.class);

    public static Config getConfig() {
        Config config = Configs.newBuilder()
           .withOptionalRelativeFile("./secure.conf")
           .withResource("cms.application." + Env.get().getName() + ".conf")
           .withResource("cms.application.conf")
           .withResource("application." + Env.get().getName() + ".conf")
           .withResource("application.conf")
           .build();
        logger.debug(Json.serializer().toPrettyString(Configs.asMap(config)));
        return config;
    }

    public static <T> void run(Runnable runnable) {
        try {
            Configs.initProperties(getConfig());
            runnable.run();
        } catch (Throwable ex) {
            logger.error("", ex);
        } finally {
            // Close pools and stuff
        }
    }
}

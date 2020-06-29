package ir.asanpardakht.cms.server;

import ir.asanpardakht.cms.common.db.ConfigurationWrapper;

public class CmsDSLs {

    private CmsDSLs() {
    }

    public static ConfigurationWrapper transactional() {
        return new ConfigurationWrapper(CMSConnectionPools.transactionalConfig());
    }

    public static ConfigurationWrapper processing() {
        return new ConfigurationWrapper(CMSConnectionPools.processingConfig());
    }
}
